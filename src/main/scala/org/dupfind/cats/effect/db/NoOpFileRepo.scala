package org.dupfind.cats.effect.db

import cats.effect.kernel.Async
import fs2.Stream

/** A FileRepo instance where all calls perform no operation
  * @tparam F
  *   the effect type (usually IO, but can be any Async)
  */
class NoOpFileRepo[F[_]: Async] extends FileRepo[F] {
  override def initialize: F[Unit] = Async[F].unit
  override def insert(record: FileRecord): F[Int] = Async[F].pure(0)
  override def find(dir: String): Stream[F, FileRecord] = Stream.empty
  override def get(file: String): F[Option[FileRecord]] = Async[F].pure(None)
  override def update(record: FileRecord): F[Int] = Async[F].pure(0)
  override def delete(file: String): F[Int] = Async[F].pure(0)
  override def truncate: F[Unit] = Async[F].unit
}
