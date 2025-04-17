package org.dupfind.cats.effect.domain

case class TestFile(path: String, size: Long, modified: java.time.Instant) extends File
