package org.dupfind.cats.effect.db

import cats.effect.Async
import cats.implicits.*
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import fs2.Stream
import org.dupfind.cats.effect.DbConfig

/** Interface for interacting with a file repo where file information can be stored between runs.
  * @tparam F
  *   the effect type (usually IO, but can be any Async)
  */
trait FileRepo[F[_]: Async] {

  /** Initialize the repo
    * @return
    *   unit
    */
  def initialize: F[Unit]

  /** Insert one FileRecord into the repo
    * @param record
    *   the record being inserted
    * @return
    *   result info (depends upon underlying implementation)
    */
  def insert(record: FileRecord): F[Int]

  /** Insert multiple FileRecord instances into the repo
    * @param records
    *   the records being inserted
    * @return
    *   result info (depends upon underlying implementation)
    */
  def insertAll(records: FileRecord*): F[Seq[Int]] =
    records.map(insert).sequence

  /** Finds records for all files within the given directory string
    * @param dir
    *   the directory string to match on
    * @return
    *   records for all matching files
    */
  def find(dir: String): Stream[F, FileRecord]

  /** Obtain the FileRecord of the given file string
    * @param file
    *   the exact full file path of the record to match on
    * @return
    *   the FileRecord of the matching record, if found, else None
    */
  def get(file: String): F[Option[FileRecord]]

  /** Fetches multiple FileRecord instances
    * @param files
    *   the full path strings to fetch
    * @return
    *   each matching file record, if found. Missing files will return as None.
    */
  def getAll(files: String*): F[Seq[Option[FileRecord]]] =
    files.map(get).sequence

  /** Modifies the given file record using the given details
    * @param record
    *   the record to modify, including updated values
    * @return
    *   result info (depends upon underlying implementation)
    */
  def update(record: FileRecord): F[Int]

  /** Delete the file record at the given key
    * @param file
    *   the record key to match on
    * @return
    *   result info (depends upon underlying implementation)
    */
  def delete(file: String): F[Int]

  /** Delete all records from the file repo
    * @return
    *   unit
    */
  def truncate: F[Unit]
}

/** Companion object for instantiating the FileRepo according to configuration provided.
  */
object FileRepo {
  def apply[F[_]: Async](config: DbConfig): F[FileRepo[F]] = {
    def transactor(config: DbConfig) = for {
      ec <- ExecutionContexts.fixedThreadPool[F](config.threads)
      tx <- HikariTransactor.newHikariTransactor[F](
        config.driverClassName,
        config.url,
        config.username,
        config.password,
        ec
      )
    } yield tx

    def repo(config: DbConfig): F[FileRepo[F]] =
      if (config.enabled)
        Async[F].pure(new DbFileRepo[F](transactor(config)))
      else
        Async[F].pure(new NoOpFileRepo[F]())

    repo(config)
  }
}
