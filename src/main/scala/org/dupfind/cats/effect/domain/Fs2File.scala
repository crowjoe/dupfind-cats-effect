package org.dupfind.cats.effect.domain

import java.time.Instant

import fs2.io.file.PathInfo

/** An fs2-based implementation of a File, wrapping an fs2 PathInfo instance.
  * @param pathInfo
  *   contains the underlying fs2 file information source.
  */
case class Fs2File(pathInfo: PathInfo) extends File {
  override lazy val path = pathInfo.path.toString
  override lazy val size = pathInfo.attributes.size

  // In corner cases, the creation time might be after the modified time.  We are interested in whichever occurs last.
  override lazy val modified =
    Instant.ofEpochMilli(
      pathInfo.attributes.lastModifiedTime
        .max(pathInfo.attributes.creationTime)
        .toMillis
    )
}
