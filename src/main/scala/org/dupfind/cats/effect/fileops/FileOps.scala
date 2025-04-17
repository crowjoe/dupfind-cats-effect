package org.dupfind.cats.effect.fileops

import fs2.Stream
import fs2.io.file.Path
import org.dupfind.cats.effect.domain.Checksum
import org.dupfind.cats.effect.domain.File

/** Common trait for performing file operations
  * @tparam F
  *   some higher kinded type constructor
  */
trait FileOps[F[_]] {

  /** obtain checksum information for a given file
    * @param file
    *   the File being checked
    * @return
    *   the Checksum for the file
    */
  def checksum(file: File): F[Checksum]

  /** obtain a stream of files under a given directory path
    * @param path
    *   the directory path being searched
    * @return
    *   the stream of files inside the given directory
    */
  def fileStream(path: Path): Stream[F, File]

  /** determine if the given path exists
    * @param path
    *   the path being checked
    * @return
    *   whether the path exists, enclosed within our wrapper type F
    */
  def exists(path: String): F[Boolean]

  /** obtain a stream of Path instances representing directories from a given list of strings
    * @param paths
    *   the strings for the directories being checked
    * @return
    *   a Stream of Path instances for the given directory strings
    */
  def dirStream(paths: Seq[String]): Stream[F, Path]
}
