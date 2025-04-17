package org.dupfind.cats.effect.utils

import cats.effect.Async
import cats.syntax.functor.*
import fs2.Stream
import fs2.io.file.Path

extension [F[_]: Async, A](stream: Stream[F, A])
  /** A convenience method for running an effect (like evalTap, but in parallel and unordered)
    * @param maxConcurrent
    *   number of concurrent workers
    * @param op
    *   the side effect to perform
    * @tparam B
    *   the kind of effect
    * @return
    *   the original stream, but with the side effects applied
    */
  def parEvalTapUnordered[B](maxConcurrent: Int)(op: A => F[B]): Stream[F, A] =
    stream.parEvalMapUnordered(maxConcurrent)(a => op(a).as(a))

extension [F[_]: Async, A](stream: Stream[F, List[A]])
  /** Given a stream of input groups, split each group up into new separate stream groups, according to some grouping
    * function.
    * @param chunkSize
    *   the groups are iteratively produced in chunks of this size, for better performance
    * @param groupFn
    *   the grouping function
    * @tparam B
    *   the grouping type
    * @return
    *   a new stream with original input groups split according to the grouping function
    */
  def groupBy[B](chunkSize: Int)(groupFn: A => B): Stream[F, List[A]] =
    for {
      inputGroup <- stream
      iterator = inputGroup.groupBy(groupFn).values.iterator
      outputGroups <- Stream.fromIterator[F](iterator, chunkSize)
    } yield outputGroups

  // Each group must have at least two elements
  /** A convenience filter for streams of collections, ensuring each collection has at least two values
    * @return
    *   a new stream using only elements from the original which have at least two values
    */
  def min2: Stream[F, List[A]] =
    stream.filter(list => list.headOption.nonEmpty && list.drop(1).nonEmpty)

extension (string: String)
  // Enforce consistent directory Path instances
  /** Interpret a given String as an fs2 Path, applying consistent formatting.
    * @return
    *   fs2 Path instance
    */
  def asDirectory: Path = Path(string).absolute.normalize

extension (path: Path)
  /** Converts a given fs2 Path to a String, assuming that the path represents a directory and not a file. The string
    * will always end with a slash file-separator at the end. This is useful for `find` operations by ensuring that only
    * files within a given directory are matched, avoiding matches in other directories which start with the same
    * prefix.
    * @return
    *   the directory Path converted to a string, with a slash file separator appended
    */
  def slashString: String = path.toString match {
    case "/"     => "/" // the root directory already has a slash
    case noSlash => noSlash + "/" // non-root directories need the slash added
  }
