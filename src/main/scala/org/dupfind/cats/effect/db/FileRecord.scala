package org.dupfind.cats.effect.db

import java.time.Instant

/** Represents one record in the FileRepo instance
  * @param file
  *   the full path of the File used for this record
  * @param checksum
  *   the checksum of the file
  * @param modified
  *   when the file was last modified, according to FileOps implementation
  */
case class FileRecord(file: String, checksum: String, modified: Instant) {
  def expired(at: Instant): Boolean = at.isAfter(modified)
  def unexpired(at: Instant): Boolean = !expired(at)
}
