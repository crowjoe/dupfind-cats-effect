package org.dupfind.cats.effect.utils

import scala.math.Ordering.Implicits.*

import cats.effect.IO
import fs2.Stream
import munit.CatsEffectSuite
import org.dupfind.cats.effect.utils.groupBy

class UtilsSpec extends CatsEffectSuite {

  def isEven(n: Int): Boolean = n % 2 == 0

  test("groupBy should NOT group items from separate input lists, even if they have the same group key") {
    val input = List(
      List(1, 2, 3, 4),
      List(5, 6, 7, 8)
    )

    // Even and odd numbers which were originally in separate lists will remain in separate output group lists.
    // Specifically, 1 & 3 will NOT be grouped with 5 & 7.  Likewise, 2 & 4 will NOT be grouped with 6 & 8.
    val expected: List[List[Int]] = List(
      List(1, 3),
      List(2, 4),
      List(5, 7),
      List(6, 8)
    ).sorted // order of groups doesn't matter

    val stream = Stream.emits[IO, List[Int]](input)

    val grouped = stream.groupBy(99)(isEven) // group by whether the integer is even

    grouped.compile.toList
      .map(_.sorted) // order of groups doesn't matter
      .map(results => assertEquals(results, expected))
  }
}
