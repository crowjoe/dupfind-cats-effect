package org.dupfind.cats.effect.domain

import cats.data.NonEmptySet

/** Represents a collection of known duplicate files.
  * @param paths
  *   the collection of duplicate file path strings
  * @param size
  *   the same size which all the duplicates have
  * @param checksum
  *   the same checksum which all of the duplicates have
  */
case class Duplicates(
    paths: NonEmptySet[String],
    size: Long,
    checksum: String
) {
  override def toString: String = {
    val ps: String = paths.toSortedSet.mkString("\n")
    s"checksum $checksum ($size) bytes)\n${ps}"
  }
}
