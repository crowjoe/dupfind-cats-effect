package org.dupfind.cats.effect.domain

/** A wrapper class for a File which includes checksum information.
  * @param file
  *   the underlying File instance
  * @param checksum
  *   the known checksum of the file
  */
case class Checksum(file: File, checksum: String)
