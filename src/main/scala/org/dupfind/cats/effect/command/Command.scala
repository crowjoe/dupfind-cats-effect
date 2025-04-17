package org.dupfind.cats.effect.command

import cats.effect.Async
import fs2.Stream
import org.dupfind.cats.effect
import org.dupfind.cats.effect.Action
import org.dupfind.cats.effect.Config

/** Represents the kinds of actions which can be performed.
  * @tparam F
  *   the enclosing effect type returned by running the command (usually IO, but can be any Async)
  */
sealed trait Command[F[_]: Async] {
  def config: Config
  // Use effect.Dupfind to avoid name conflict with Action.Dupfind
  def run(dupfind: effect.Dupfind[F]): Stream[F, String]
}

case class NoOp[F[_]: Async](config: Config) extends Command[F] {
  def run(dupfind: effect.Dupfind[F]) =
    Stream.empty
}

case class Initialize[F[_]: Async](config: Config) extends Command[F] {
  def run(dupfind: effect.Dupfind[F]) =
    dupfind.initialize.map(_ => "DB has been initialized")
}

case class Dupfind[F[_]: Async](config: Config) extends Command[F] {
  def run(dupfind: effect.Dupfind[F]) =
    dupfind.dupfind(config.dupfind.dirs).map(_.toString)
}

case class Missing[F[_]: Async](config: Config) extends Command[F] {
  def run(dupfind: effect.Dupfind[F]) =
    dupfind
      .missing(config.dupfind.dirs)
      .map(missing => s"Found DB record for missing file $missing")
}

case class Prune[F[_]: Async](config: Config) extends Command[F] {
  def run(dupfind: effect.Dupfind[F]) =
    dupfind
      .prune(config.dupfind.dirs)
      .map(pruned => s"Deleted DB record for missing file $pruned")
}

case class Purge[F[_]: Async](config: Config) extends Command[F] {
  def run(dupfind: effect.Dupfind[F]) =
    dupfind.purge.map(_ => "DB has been purged")
}

/** Companion object to obtain the Command from the Config.
  */
object Command {
  def apply[F[_]: Async](config: Config): F[Command[F]] =
    Async[F].fromOption(
      Action.values
        .find(
          _.toString.equalsIgnoreCase(config.dupfind.action)
        )
        .map {
          case Action.NoOp       => NoOp(config)
          case Action.Initialize => Initialize(config)
          case Action.Dupfind    => Dupfind(config)
          case Action.Missing    => Missing(config)
          case Action.Prune      => Prune(config)
          case Action.Purge      => Purge(config)
        },
      new RuntimeException(
        s"unrecognized config action ${config.dupfind.action}"
      )
    )
}
