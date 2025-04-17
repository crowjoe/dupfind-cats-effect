package org.dupfind.cats.effect.db

import java.time.Instant

import cats.effect.IO
import munit.CatsEffectSuite
import org.dupfind.cats.effect.testkit.ConfigFixture

// avoid wartremover "Inferred type containing Any: Any"
@SuppressWarnings(Array("org.wartremover.warts.Any"))
class FileRepoSpec extends CatsEffectSuite with ConfigFixture {
  configFixture.test("FileRepo should perform CRUD operations") { config =>
    for {
      repo <- FileRepo[IO](config.dupfind.db)
      _ <- repo.initialize
      _ <- repo.insert(
        FileRecord("file1.txt", "abc123", java.time.Instant.now())
      )
      file <- repo.get("file1.txt")
      _ <- repo.update(
        FileRecord("file1.txt", "def456", java.time.Instant.now())
      )
      updatedFile <- repo.get("file1.txt")
      _ <- repo.delete("file1.txt")
      deletedFile <- repo.get("file1.txt")
    } yield {
      assert(file.exists(r => r.file == "file1.txt" && r.checksum == "abc123"))
      assert(updatedFile.exists(r => r.file == "file1.txt" && r.checksum == "def456"))
      assertEquals(deletedFile, None)
    }
  }

  configFixture.test("FileRepo truncate should remove all rows") { config =>
    val records = Seq(
      FileRecord("/root/1", "checksum1", Instant.ofEpochMilli(100L)),
      FileRecord("/root/2", "checksum2", Instant.ofEpochMilli(200L)),
      FileRecord("/root/3", "checksum3", Instant.ofEpochMilli(300L))
    )

    for {
      repo <- FileRepo[IO](config.dupfind.db)
      _ <- repo.initialize
      inserted <- repo.insertAll(records*)
      _ <- IO(assert(inserted.forall(_ == 1)))
      retrieved <- repo.getAll(records.map(_.file)*)
      _ <- IO(assert(retrieved.forall(_.nonEmpty)))
      _ <- repo.truncate
      retrieved <- repo.getAll(records.map(_.file)*)
      _ <- IO(assert(retrieved.forall(_.isEmpty)))
    } yield ()
  }

  configFixture.test("find files") { config =>
    val t = Instant.EPOCH
    val records = Seq(
      FileRecord("/foo/1", "", t),
      FileRecord("/foo/2", "", t),
      FileRecord("/bar/1", "", t),
      FileRecord("/bar/2", "", t)
    )

    for {
      repo <- FileRepo[IO](config.dupfind.db)
      _ <- repo.initialize
      inserted <- repo.insertAll(records*)
      _ <- IO(assert(inserted.forall(_ == 1)))
      finds <- repo.find("/foo").compile.toList
    } yield {
      val actual = finds.map(_.file).sorted
      val expected = Seq("/foo/1", "/foo/2").sorted
      assertEquals(actual, expected)
    }
  }

  configFixture.test("find all files") { config =>
    val t = Instant.EPOCH
    val records = Seq(
      FileRecord("/foo/1", "", t),
      FileRecord("/foo/2", "", t),
      FileRecord("/bar/1", "", t),
      FileRecord("/bar/2", "", t)
    )

    for {
      repo <- FileRepo[IO](config.dupfind.db)
      _ <- repo.initialize
      inserted <- repo.insertAll(records*)
      _ <- IO(assert(inserted.forall(_ == 1)))
      finds <- repo.find("/").compile.toList
    } yield {
      val actual = finds.map(_.file).sorted
      val expected = Seq("/foo/1", "/foo/2", "/bar/1", "/bar/2").sorted
      assertEquals(actual, expected)
    }
  }

  // In this test, the application might try to prune entries in a particular directory and the file repo includes
  // entries for other directories which start with the same prefix.  The application should know to not prune these
  // entries.  However, the repo will not be responsible for preventing this.  The repo should still return all
  // matching entries.
  configFixture.test(
    "find files in different paths which start with the same prefix"
  ) { config =>
    val t = Instant.EPOCH
    val records = Seq(
      FileRecord("/foo/1", "", t),
      FileRecord("/fooA/1", "", t),
      FileRecord("/fooB/1", "", t),
      FileRecord("/bar/1", "", t)
    )

    for {
      repo <- FileRepo[IO](config.dupfind.db)
      _ <- repo.initialize
      inserted <- repo.insertAll(records*)
      _ <- IO(assert(inserted.forall(_ == 1)))
      finds <- repo.find("/foo").compile.toList
    } yield {
      val actual = finds.map(_.file).sorted
      val expected = Seq("/foo/1", "/fooA/1", "/fooB/1").sorted
      assertEquals(actual, expected)
    }
  }

  // In this test, the application might try to prune entries in a particular directory and the file repo includes
  // entries for other directories which start with the same prefix.  The application should know to not prune these
  // entries.  Here, the application correctly performs a find which terminates with the directory separator character.
  // This prevents the find call from returning files under the other directories.
  configFixture.test(
    "find files in different paths which start with the same prefix"
  ) { config =>
    val t = Instant.EPOCH
    val records = Seq(
      FileRecord("/foo/1", "", t),
      FileRecord("/fooA/1", "", t),
      FileRecord("/fooB/1", "", t),
      FileRecord("/bar/1", "", t)
    )

    for {
      repo <- FileRepo[IO](config.dupfind.db)
      _ <- repo.initialize
      inserted <- repo.insertAll(records*)
      _ <- IO(assert(inserted.forall(_ == 1)))
      finds <- repo.find("/foo/").compile.toList
    } yield {
      val actual = finds.map(_.file).sorted
      val expected = Seq("/foo/1").sorted
      assertEquals(actual, expected)
    }
  }
}
