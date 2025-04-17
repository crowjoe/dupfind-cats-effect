package org.dupfind.cats.effect.domain

import java.time.Instant

/** Generic trait which represents the kinds of basic file information that Dupfind cares about. This allows us to use
  * real fs2 implementations in production and use fake instances in unit tests.
  */
trait File {
  def path: String
  def size: Long
  def modified: Instant
}
