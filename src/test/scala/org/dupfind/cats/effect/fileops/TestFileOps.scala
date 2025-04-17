package org.dupfind.cats.effect.fileops

import cats.effect.*
import fs2.Stream
import fs2.io.file.Path
import org.dupfind.cats.effect.domain.Checksum
import org.dupfind.cats.effect.domain.File
import org.dupfind.cats.effect.utils.asDirectory
import org.dupfind.cats.effect.utils.slashString

case class TestFileOps[F[_]: Async](checksums: Seq[Checksum]) extends FileOps[F] {
  val map = checksums.map(f => f.file.path -> f).toMap

  override def checksum(file: File): F[Checksum] =
    Async[F].fromOption(
      map.get(file.path),
      new IllegalArgumentException(s"unable to get checksum for $file")
    )

  override def fileStream(path: Path): Stream[F, File] =
    Stream.emits(
      map.values
        .map(_.file)
        .filter(_.path.startsWith(path.slashString))
        .toSeq
    )

  override def dirStream(paths: Seq[String]): Stream[F, Path] =
    Stream.emits(paths.map(_.asDirectory))

  override def exists(pathString: String): F[Boolean] =
    Async[F].pure(map.contains(pathString))
}
