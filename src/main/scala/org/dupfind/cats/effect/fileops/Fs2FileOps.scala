package org.dupfind.cats.effect.fileops

import cats.effect.Async
import cats.syntax.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import fs2.Stream
import fs2.hashing.HashAlgorithm
import fs2.hashing.Hashing
import fs2.io.file.Files
import fs2.io.file.Path
import org.dupfind.cats.effect.Config
import org.dupfind.cats.effect.domain.Checksum
import org.dupfind.cats.effect.domain.File
import org.dupfind.cats.effect.domain.Fs2File
import org.dupfind.cats.effect.utils.asDirectory

/** An fs2-based implementation for file operations.
  * @param config
  *   configuration controlling internal behavior such as number of concurrent workers.
  * @tparam F
  *   binds several fs2 type classes together
  */
case class Fs2FileOps[F[_]: Async: Files: Hashing](config: Config) extends FileOps[F] {
  override def checksum(file: File): F[Checksum] =
    Files[F]
      .readAll(Path(file.path))
      .through(fs2.hashing.Hashing[F].hash(HashAlgorithm.MD5))
      .compile
      .lastOrError
      .map(hash => Checksum(file, hash.toString))

  override def fileStream(path: Path): Stream[F, File] =
    Files[F]
      .walkWithAttributes(path)
      .filter(_.attributes.isRegularFile) // ignore directories, symlinks, etc
      .filter(
        _.attributes.size > 0
      ) // we don't care about duplicate empty files, so skip all empty files
      .map(pathInfo => Fs2File(pathInfo))

  // Given a collection of path strings, provide a stream of directory paths
  override def dirStream(paths: Seq[String]): Stream[F, Path] =
    Stream
      .emits(paths.map(_.asDirectory))
      .parEvalMapUnordered(config.dupfind.stream.maxConcurrent)(path =>
        Files[F].isDirectory(path).flatMap {
          case true => Async[F].pure(path)
          case false =>
            Async[F].raiseError[Path](
              IllegalArgumentException(s"target '$path' must be a directory'")
            )
        }
      )

  override def exists(pathString: String) =
    Files[F].exists(Path(pathString))
}
