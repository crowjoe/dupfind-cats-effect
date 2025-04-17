package org.dupfind.cats.effect.db

/*
postgres notes:
docker rmi -f $(docker images -q) # remove images
docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=password postgres:17.4
docker exec -it your_postgres_container_name bash
psql -U postgres
\l # show databases
\dt # show tables
\d files # describe files table
select * from files;
 */

import java.time.Instant

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.implicits.*
import doobie.*
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.JavaInstantMeta
import fs2.Stream
import org.flywaydb.core.Flyway

/** An implementation of FileRepo which uses Doobie to interact with an underlying database instance.
  * @param transactor
  *   the HikariTransactor for the database, wrapped as a cats effect Resource
  * @tparam F
  *   the effect type (usually IO, but can be any Async)
  */
class DbFileRepo[F[_]: Async](transactor: Resource[F, HikariTransactor[F]]) extends FileRepo[F] {

  override def initialize: F[Unit] =
    transactor.use(tx =>
      Async[F]
        .delay(Flyway.configure().dataSource(tx.kernel).load().migrate())
        .void
    )

  override def insert(record: FileRecord): F[Int] = transactor.use { tx =>
    sql"""
      INSERT INTO files (file, checksum, modified)
      VALUES (${record.file}, ${record.checksum}, ${record.modified})
    """.update.run.transact(tx)
  }

  override def find(path: String): Stream[F, FileRecord] =
    Stream.resource(transactor).flatMap { tx =>
      sql"""
      SELECT file, checksum, modified
      FROM files
      WHERE file LIKE ${path + "%"}
    """.query[FileRecord].stream.transact(tx)
    }

  override def get(file: String): F[Option[FileRecord]] = transactor.use { tx =>
    sql"""
      SELECT file, checksum, modified
      FROM files
      WHERE file = $file
    """.query[FileRecord].option.transact(tx)
  }

  override def update(record: FileRecord): F[Int] = transactor.use { tx =>
    sql"""
      UPDATE files
      SET checksum = ${record.checksum}, modified = ${record.modified}
      WHERE file = ${record.file}
    """.update.run.transact(tx)
  }

  override def delete(file: String): F[Int] =
    transactor.use(tx => sql"DELETE FROM files WHERE file = $file".update.run.transact(tx))

  override def truncate: F[Unit] =
    transactor.use(tx => sql"TRUNCATE TABLE files".update.run.transact(tx).void)
}
