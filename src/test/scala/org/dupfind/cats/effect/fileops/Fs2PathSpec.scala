package org.dupfind.cats.effect.fileops

import fs2.io.file.Path
import munit.CatsEffectSuite

// These tests confirm expected behavior of the fs2 Path, specifically the handling of a trailing / separator
// character for directories.  Path instances for directories are expected to drop the trailing separator.  This
// behavior drives how the Dupfind application performs `find` operations in its file repo so that it avoids matching
// files inside of directories which begin with a similar substring.  If this behavior ever changes, then the Dupfind
// application needs to refactor how it avoids matching the wrong directories.  Note that the / separator is NOT
// dropped for the root directory /.
class Fs2PathSpec extends CatsEffectSuite {
  test("fs2 Path for a directory is expected to drop a trailing separator") {
    assertEquals(Path("/foo/").toString, "/foo")
  }

  test(
    "fs2 Path for a relative directory is expected to drop a trailing separator"
  ) {
    assertEquals(Path("../foo/").toString, "../foo")
  }

  test(
    "fs2 Path for a normalized directory is expected to drop a trailing separator"
  ) {
    assertEquals(Path("../foo/").normalize.toString, "../foo")
  }

  test(
    "fs2 Path for an absolute directory is expected to drop a trailing separator"
  ) {
    assertEquals(Path("foo/").absolute.toString, Path("foo").absolute.toString)
  }

  test("fs2 Path for root is expected to NOT drop the trailing separator") {
    assertEquals(Path("/").toString, "/")
  }
}
