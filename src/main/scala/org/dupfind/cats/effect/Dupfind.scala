package org.dupfind.cats.effect

import cats.data.NonEmptyList
import cats.effect.*
import cats.implicits.*
import cats.syntax.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import fs2.Stream
import org.dupfind.cats.effect.build.BuildInfo
import org.dupfind.cats.effect.command.Command
import org.dupfind.cats.effect.db.*
import org.dupfind.cats.effect.domain.Checksum
import org.dupfind.cats.effect.domain.Duplicates
import org.dupfind.cats.effect.domain.File
import org.dupfind.cats.effect.fileops.FileOps
import org.dupfind.cats.effect.fileops.Fs2FileOps
import org.dupfind.cats.effect.utils.groupBy
import org.dupfind.cats.effect.utils.min2
import org.dupfind.cats.effect.utils.parEvalTapUnordered
import org.dupfind.cats.effect.utils.slashString
import org.typelevel.log4cats.slf4j.Slf4jLogger

/** The central class for finding duplicate files. It has a method for each of the primary use cases.
  * @param fileOps
  *   a helper class for performing common file operations
  * @param fileRepo
  *   the repo where file checksum information is searched, saved, and retrieved
  * @param config
  *   contains information from configuration files and command-line arguments
  * @tparam F
  *   the effect type used. In nearly all cases it will be IO. But it could be any kind of Async.
  */
case class Dupfind[F[_]: Async](
    fileOps: FileOps[F],
    fileRepo: FileRepo[F],
    config: Config
) {
  val logger = Slf4jLogger.getLogger[F]
  val maxConcurrent = config.dupfind.stream.maxConcurrent

  def checksumFinder(file: File): F[Checksum] =
    fileRepo.get(file.path).flatMap {
      case Some(record) if record.unexpired(file.modified) =>
        Async[F].pure(Checksum(file, record.checksum))
      case expiredOrNone =>
        // We must get current timestamp *BEFORE* getting the checksum.  Otherwise, it is possible that the file is
        // modified some time after calculation and before timestamp is obtained.
        fileOps.checksum(file).flatMap { cs =>
          val upsert =
            if (expiredOrNone.isEmpty) fileRepo.insert else fileRepo.update
          upsert(
            FileRecord(
              file = file.path,
              checksum = cs.checksum,
              modified = file.modified
            )
          ) >>
            Async[F].pure(cs)
        }
    }

  def initialize: Stream[F, Unit] = Stream.eval(fileRepo.initialize)

  def purge: Stream[F, Unit] = Stream.eval(fileRepo.truncate)

  def missing(paths: Seq[String]): Stream[F, String] =
    for {
      dir <- fileOps.dirStream(paths)
      record <- fileRepo
        .find(dir.slashString)
        .evalFilterNotAsync(maxConcurrent)(f => fileOps.exists(f.file))
    } yield record.file

  def prune(paths: Seq[String]): Stream[F, String] =
    missing(paths).parEvalTapUnordered(maxConcurrent)(fileRepo.delete)

  def dupfind(paths: Seq[String]): Stream[F, Duplicates] = {
    val files: Stream[F, File] = for {
      dir <- fileOps.dirStream(paths)
      fileData <- fileOps.fileStream(dir)
    } yield fileData

    val sizeGroups = Stream
      .eval(files.compile.toList)
      .groupBy(config.dupfind.stream.chunkSize)(_.size)
      .min2

    // TODO refactor the grouping logic so that we can fan-out and group by size and/or checksum without waiting for
    //  the stream to complete.  The potential benefit of this is that it allows us to start calculating checksums
    //  earlier during processing.  Checksum calculation is the slowest operation.  So if we can start calculating
    //  checksums for same-size files earlier, then our application may complete sooner.  For very large or infinite
    //  streams, this won't work because size and checksum both have extremely high cardinality.  But this could work
    //  for a local file system where we are capable of keeping the entire file system path, size, and even checksum in
    //  memory.  In reality, this early fan-out grouping by size or checksum may not produce much benefit.  But it is
    //  an interesting strategy to experiment with.  See `docs/v2/dupfind-cats-effect.svg` for a flow diagram example.

    val checksums = sizeGroups.parEvalMapUnordered(maxConcurrent) { sameSizeFiles =>
      Stream
        .emits(sameSizeFiles)
        .covary[F]
        .parEvalMapUnordered(maxConcurrent)(checksumFinder)
        .compile
        .toList
    }

    // Group by checksum, ignoring groups with only one item
    val checksumGroups =
      checksums.groupBy(config.dupfind.stream.chunkSize)(_.checksum).min2

    val duplicates = checksumGroups.parEvalMapUnordered(maxConcurrent) { group =>
      for {
        nel <- Async[F].fromOption(
          NonEmptyList.fromList(group),
          new IllegalArgumentException(s"unexpected empty list")
        )
      } yield Duplicates(
        nel.map(_.file.path).toNes,
        nel.head.file.size,
        nel.head.checksum
      )
    }

    duplicates
  }
}

/** The main application. It will instantiate a Dupfind class and perform the given operations using details from the
  * config file and command-line arguments.
  */
object Dupfind extends IOApp {
  val logger = Slf4jLogger.getLogger[IO]

  override def run(args: List[String]): IO[ExitCode] = {
    // Derived using the sbt-buildinfo plugin:  https://github.com/sbt/sbt-buildinfo?tab=readme-ov-file#usage
    val program = BuildInfo.name
    val version = BuildInfo.version

    for {
      start <- IO.monotonic
      config <- Config(args = args, program = program, version = version)
      repo <- FileRepo[IO](config.dupfind.db)
      command <- Command[IO](config)
      dupfind = Dupfind[IO](Fs2FileOps[IO](config), repo, config)
      results <- command
        .run(dupfind)
        .foreach(logger.info(_))
        .compile[IO, IO, Nothing]
        .drain
        .as(ExitCode.Success)
      end <- IO.monotonic
      elapsed = end - start
      _ <- logger.info(s"Execution time: ${elapsed.toMillis} ms")
    } yield results
  }
}
