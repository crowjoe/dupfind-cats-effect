package org.dupfind.cats.effect

import java.time.Instant

import scala.concurrent.duration.*

import cats.data.NonEmptySet
import cats.effect.IO
import cats.effect.kernel.Sync
import cats.effect.std.Random
import fs2.Stream
import fs2.io.file.Path
import munit.CatsEffectSuite
import org.dupfind.cats.effect.db.FileRecord
import org.dupfind.cats.effect.db.FileRepo
import org.dupfind.cats.effect.domain.Checksum
import org.dupfind.cats.effect.domain.Duplicates
import org.dupfind.cats.effect.domain.File
import org.dupfind.cats.effect.domain.TestFile
import org.dupfind.cats.effect.fileops.FileOps
import org.dupfind.cats.effect.fileops.TestFileOps
import org.dupfind.cats.effect.testkit.ConfigFixture
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

// avoid wartremover "Inferred type containing Any: Any"
@SuppressWarnings(Array("org.wartremover.warts.Any"))
class DupfindSpec extends CatsEffectSuite with ConfigFixture {
  val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  val modified: Instant = Instant.ofEpochSecond(1000L)
  val checksum = "checksum"

  val checksums = List(
    Checksum(TestFile("/root/1/file.dat", 100L, modified), checksum),
    Checksum(TestFile("/root/1/diff_extension.bin", 100L, modified), checksum),
    Checksum(TestFile("/root/1/diff.dat", 999L, modified), "other_checksum"),
    Checksum(
      TestFile("/root/1/same_size_diff_checksum.dat", 100L, modified),
      "diff_checksum"
    ),
    Checksum(
      TestFile("/root/1/diff_size_same_checksum.dat", 100L, modified),
      checksum
    ),
    Checksum(
      TestFile("/root/2/same_file_other_path.dat", 100L, modified),
      checksum
    ),
    Checksum(
      TestFile("/root/3/diff_file_other_path.txt", 999L, modified),
      checksum
    )
  )

  val expected = List(
    Duplicates(
      NonEmptySet.of(
        "/root/1/diff_extension.bin",
        "/root/1/diff_size_same_checksum.dat",
        "/root/1/file.dat",
        "/root/2/same_file_other_path.dat"
      ),
      100L,
      checksum
    )
  )

  configFixture.test("Dupfind should return expected duplicates") { config =>
    for {
      repo <- FileRepo[IO](config.dupfind.db)
      _ <- repo.initialize
      results <- Dupfind[IO](TestFileOps[IO](checksums), repo, config)
        .dupfind(List("/"))
        .compile
        .toList
    } yield assertEquals(results, expected)
  }

  configFixture.test("Dupfind should handle an updated file") { config =>
    val file1 = "/root/1"
    val file2 = "/root/2"
    val file3 = "/root/3"
    val size = 10L
    val checksum1 = "checksum1"
    val checksum2 = "checksum2"
    val oldTime = Instant.ofEpochMilli(100L)
    val newTime = Instant.ofEpochMilli(200L)

    val records = Seq(
      FileRecord(file1, checksum1, oldTime),
      FileRecord(file2, checksum1, oldTime),
      FileRecord(file3, checksum2, oldTime)
    )

    val checksums = Seq(
      Checksum(TestFile(file1, size, oldTime), checksum1),
      Checksum(
        TestFile(file2, size, newTime),
        checksum2
      ), // the change is here for this file
      Checksum(TestFile(file3, size, oldTime), checksum2)
    )

    val expected =
      List(Duplicates(NonEmptySet.of(file2, file3), size, checksum2))

    for {
      repo <- FileRepo[IO](config.dupfind.db)
      _ <- repo.initialize
      inserted <- repo.insertAll(records*)
      results <- Dupfind[IO](TestFileOps[IO](checksums), repo, config)
        .dupfind(List("/"))
        .compile
        .toList
      retrieved <- repo.getAll(file1, file2, file3)
    } yield {
      assert(inserted.forall(_ == 1))
      assertEquals(results.sortBy(_.checksum), expected.sortBy(_.checksum))

      assertEquals(retrieved(0), Option(records(0)))
      assertNotEquals(retrieved(1), Option(records(1)))
      assertEquals(retrieved(2), Option(records(2)))

      assertEquals(retrieved(1).map(_.file), Option(records(1).file))
      assertEquals(retrieved(1).map(_.checksum), Option(checksum2))
      assertNotEquals(
        retrieved(1).map(_.modified.toEpochMilli),
        Option(oldTime.toEpochMilli)
      )
      assertEquals(retrieved(1).map(_.modified.toEpochMilli), Option(newTime.toEpochMilli))
    }
  }

  configFixture.test("prune should delete expected files") { config =>
    val t = Instant.EPOCH
    val records = Seq(
      FileRecord("/foo/1", "", t),
      FileRecord("/foo/2", "", t),
      FileRecord("/foo/3", "", t),
      FileRecord("/fooA/1", "", t),
      FileRecord("/fooA/2", "", t),
      FileRecord("/fooB/1", "", t),
      FileRecord("/fooB/2", "", t),
      FileRecord("/bar/1", "", t),
      FileRecord("/bar/2", "", t)
    )

    val expected = Seq("/foo/2", "/foo/3")

    // Only one file from each directory still exists in the file system.
    val checksums = List(
      Checksum(TestFile("/foo/1", 0L, t), ""),
      Checksum(TestFile("/fooA/1", 0L, t), ""),
      Checksum(TestFile("/fooB/1", 0L, t), ""),
      Checksum(TestFile("/bar/1", 0L, t), "")
    )

    for {
      repo <- FileRepo[IO](config.dupfind.db)
      _ <- repo.initialize
      inserted <- repo.insertAll(records*)
      _ <- IO(assert(inserted.forall(_ == 1)))
      dupfind = Dupfind[IO](TestFileOps[IO](checksums), repo, config)
      pruned1 <- dupfind.prune(List("/foo")).compile.toList
      _ <- IO(assertEquals(pruned1.sorted, expected.sorted))
      retrieved <- repo.getAll(records.map(_.file)*)
      _ <- IO(retrieved.forall(_.isEmpty))
    } yield ()
  }

  configFixture.test(
    "missing operation should identify the correct missing files"
  ) { config =>
    val t = Instant.EPOCH
    val records = Seq(
      FileRecord("/foo/1", "", t),
      FileRecord("/foo/2", "", t),
      FileRecord("/foo/3", "", t),
      FileRecord("/fooA/1", "", t),
      FileRecord("/fooA/2", "", t),
      FileRecord("/fooB/1", "", t),
      FileRecord("/fooB/2", "", t),
      FileRecord("/bar/1", "", t),
      FileRecord("/bar/2", "", t)
    )

    val expected = Seq("/foo/2", "/foo/3")

    // Only one file from each directory still exists in the file system.
    val checksums = List(
      Checksum(TestFile("/foo/1", 0L, t), ""),
      Checksum(TestFile("/fooA/1", 0L, t), ""),
      Checksum(TestFile("/fooB/1", 0L, t), ""),
      Checksum(TestFile("/bar/1", 0L, t), "")
    )

    for {
      repo <- FileRepo[IO](config.dupfind.db)
      _ <- repo.initialize
      inserted <- repo.insertAll(records*)
      _ <- IO(assert(inserted.forall(_ == 1)))
      dupfind = Dupfind[IO](TestFileOps[IO](checksums), repo, config)
      missing1 <- dupfind.missing(List("/foo")).compile.toList
      missing2 <- dupfind.missing(List("/foo/")).compile.toList
      // results for /foo and /foo/ must be the same
      _ <- IO(assertEquals(missing1.sorted, missing2.sorted))
      _ <- IO(assertEquals(missing1.sorted, expected.sorted))
      retrieved <- repo.getAll(records.map(_.file)*)
      _ <- IO(retrieved.forall(_.nonEmpty))
    } yield ()
  }

  // This test is used to verify concurrency and parallel behavior.
  // It can also measure the result of different maxConcurrent values.
  configFixture.test("Fake File IO operations with simulated delays") { config =>

    // Produces an IO with a random sleep.  Details are logged to the console.
    def randomSleep(
        random: Random[IO],
        prefix: Option[String]
    ): IO[Unit] =
      for {
        value <- random.nextIntBounded(200)
        _ <- logger.info(s"$prefix: sleeping $value")
        _ <- IO.sleep(value.millis)
        _ <- logger.info(s"$prefix: done sleeping $value")
      } yield ()

    // Build a custom FileOps which injects random delays before each file IO operation.
    // Delay details are printed to the console.
    val delayedFileOps: IO[FileOps[IO]] =
      Random.scalaUtilRandomSeedLong[IO](42L).map { random =>
        new FileOps[IO]:
          val underlying = TestFileOps[IO](checksums)
          override def checksum(file: File): IO[Checksum] =
            randomSleep(random, Option(s"checksum $file")) >> underlying
              .checksum(file)

          override def fileStream(path: Path): Stream[IO, File] =
            underlying
              .fileStream(path)
              .evalMap(p =>
                randomSleep(random, Option(s"fileStream $p")) >> Sync[IO]
                  .pure(p)
              )

          override def dirStream(paths: Seq[String]): Stream[IO, Path] =
            underlying
              .dirStream(paths)
              .evalMap(p =>
                randomSleep(random, Option(s"locations $p")) >> Sync[IO]
                  .pure(p)
              )

          override def exists(pathString: String): IO[Boolean] =
            randomSleep(random, Option(s"exists $pathString")) >> underlying
              .exists(pathString)
      }

    for {
      repo <- FileRepo[IO](config.dupfind.db)
      _ <- repo.initialize
      ops <- delayedFileOps
      results <- Dupfind(ops, repo, config).dupfind(List("/")).compile.toList
    } yield assertEquals(results, expected)
  }
}
