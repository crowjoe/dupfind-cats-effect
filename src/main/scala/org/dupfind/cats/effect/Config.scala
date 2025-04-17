package org.dupfind.cats.effect

import cats.effect.IO
import cats.effect.kernel.Ref
import com.softwaremill.quicklens.*
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pureconfig.*
import scopt.DefaultOEffectSetup
import scopt.OEffect
import scopt.OParser

/** The different actions which Dupfind can perform.
  */
enum Action:
  case NoOp, Initialize, Missing, Prune, Purge, Dupfind

/** Config information specific for the file repo database
  * @param enabled
  *   indicates if a file repo is actually used
  * @param driverClassName
  *   database file repo driver class
  * @param url
  *   database connection URL
  * @param password
  *   database password
  * @param username
  *   database username
  * @param threads
  *   database connection thread pool size
  */
case class DbConfig(
    enabled: Boolean,
    driverClassName: String,
    url: String,
    password: String, // TODO treat this securely
    username: String,
    threads: Int
) derives ConfigReader

/** Config information for how Dupfind will use fs2 streams
  * @param chunkSize
  *   chunk size used when producing streams from iterators
  * @param maxConcurrent
  *   number of concurrent workers when processing a stream in parallel
  */
case class StreamConfig(chunkSize: Int, maxConcurrent: Int) derives ConfigReader

/** Config information for running dupfind
  * @param action
  *   the action being performed
  * @param dirs
  *   the directories being checked
  * @param verbose
  *   enable verbose logging
  * @param db
  *   wrapper for database configuration
  * @param stream
  *   wrapper for stream configuration
  */
case class DupfindConfig(
    action: String,
    dirs: Seq[String],
    verbose: Boolean,
    db: DbConfig,
    stream: StreamConfig
) derives ConfigReader

/** The top-level configuration wrapper class.
  * @param dupfind
  *   configuration specific to Dupfind
  */
case class Config(dupfind: DupfindConfig) derives ConfigReader

/** Companion object for obtaining top-level Config instance.
  */
object Config {
  val logger = Slf4jLogger.getLogger[IO]

  private val builder = OParser.builder[Config]

  private def parser(program: String, _version: String, base: Config) = {
    import builder.*
    OParser.sequence(
      programName(program),
      head(program, _version),
      opt[String]("db-driver-class-name")
        .optional()
        .valueName("<string>")
        .action((value, config) =>
          config
            .modify(_.dupfind.db.driverClassName)(_ => value)
        )
        .text("the name of the database driver class"),
      opt[String]("db-url")
        .optional()
        .valueName("<url>")
        .action((value, config) =>
          config
            .modify(_.dupfind.db.url)(_ => value)
        )
        .text("the url for the DB"),
      opt[String]("db-password")
        .optional()
        .valueName("<password>")
        .action((value, config) =>
          config
            .modify(_.dupfind.db.password)(_ => value)
        )
        .text("the database password"),
      opt[String]("db-user")
        .optional()
        .valueName("<username>")
        .action((value, config) =>
          config
            .modify(_.dupfind.db.username)(_ => value)
        )
        .text("the database username"),
      opt[Int]("db-threads")
        .valueName("<size>")
        .action((value, config) =>
          config
            .modify(_.dupfind.db.threads)(_ => value)
        )
        .text(s"DB transactor thread pool size"),
      opt[Unit]("init")
        .action((_, config) =>
          config
            .modify(_.dupfind.action)(_ => Action.Initialize.toString)
        )
        .text("initialize the database (create table and index)"),
      opt[Unit]("missing")
        .action((_, config) =>
          config
            .modify(_.dupfind.action)(_ => Action.Missing.toString)
        )
        .text(
          "identify database records for files which do not exist for the given directories"
        ),
      opt[Unit]("prune")
        .action((_, config) =>
          config
            .modify(_.dupfind.action)(_ => Action.Prune.toString)
        )
        .text(
          "delete database records for files which do not exist for the given directories"
        ),
      opt[Unit]("purge")
        .action((_, config) =>
          config
            .modify(_.dupfind.action)(_ => Action.Purge.toString)
        )
        .text("delete all database records"),
      opt[Unit]("no-db")
        .action((_, config) =>
          config
            .modify(_.dupfind.db.enabled)(_ => false)
        )
        .text("file repo database not used"),
      opt[Int]("chunk-size")
        .valueName("<size>")
        .action((value, config) =>
          config
            .modify(_.dupfind.stream.chunkSize)(_ => value)
        )
        .text(
          s"chunk size when building file streams from iterators (default ${base.dupfind.stream.chunkSize})"
        ),
      opt[Int]("max-concurrent")
        .valueName("<count>")
        .action((value, config) =>
          config
            .modify(_.dupfind.stream.maxConcurrent)(_ => value)
        )
        .text(
          s"number of max concurrent stream workers (default ${base.dupfind.stream.maxConcurrent})"
        ),
      opt[Seq[String]]('d', "dirs")
        .valueName("<dir1>,<dir2>,<dir3>")
        .action((value, config) =>
          config
            .modify(_.dupfind.dirs)(_ => value)
        )
        .text(
          s"list of directories to search for duplicates (default: ${base.dupfind.dirs.mkString(",")})"
        ),
      opt[Unit]('v', "verbose")
        .action((_, config) =>
          config
            .modify(_.dupfind.verbose)(_ => true)
        )
        .text("verbose logging"),
      help('h', "help").text("prints this usage text")
    )
  }

  /** This will apply the OEffect instances produced from parsing the command line arguments. Mostly, this will print to
    * the console. But there is special handling when one of the effects triggers termination. Termination can be
    * triggered if --usage or --version is passed, or if there is some kind of argument parsing error during processing.
    * If termination is triggered, we need to intercept it so that it does not interfere with the cats runtime. However,
    * we also need to know if it was triggered so that we can modify the Config state to use the NoOp Action.
    * Unfortunately, a var is needed for this. But we only use it locally. Also, we provide a termination effect which,
    * if our var gets set to true, will update a Ref. After calling the effect runner, the handler can use this ref to
    * tell whether termination was triggered. And if termination was triggered, then the NoOp action can be applied.
    *
    * Summary of why this design is used:
    *   1. Leverage scopt for printing auto-generated help text so that we don't have to write our own. 2. Scopt
    *      defaults to calling to System.exit(0) if usage help text is displayed. But this interferes with the cats
    *      runtime. 3. We can intercept when scopt attempts termination and prevent it. But our application will then
    *      proceed normally using the derived Config state. If using the default config, it will still try to find
    *      default files. 4. We could add .action() steps for help() operations, but scopt does not perform them. 5. So
    *      when termination is attempted, we need to also alter the derived Config state to perform the NoOp operation.
    *      6. Conditionally altering the Config state is a side effect, so we use a Ref to decide and return the
    *      possibly altered state in its own effect.
    *
    * Better solutions would require changing scopt code in some of these ways:
    *   1. Disable sys.exit without needing to override setup 2. Allow help() ops to follow .action() directions.
    *
    * @param effects
    *   the OEffect instances to apply
    * @param onTerminate
    *   the cats effect to run in case termination gets intercepted
    * @return
    *   an effect which ensures that the OEffects were applied and also that the termination effect was conditionally
    *   applied.
    */
  def effectRunner(effects: List[OEffect], onTerminate: IO[Unit]): IO[Unit] =
    IO {
      @SuppressWarnings(Array("org.wartremover.warts.Var"))
      var terminated: Boolean = false // TODO revisit this and see if any way to avoid using var
      OParser.runEffects(
        effects,
        new DefaultOEffectSetup {
          override def terminate(exitState: Either[String, Unit]): Unit =
            terminated = true
        }
      )
      terminated
    }.flatMap {
      case true  => onTerminate // only apply the termination effect if it was triggered
      case false => IO.unit
    }

  /** Here we handle the results from parsing the command line arguments.
    * @param maybeConfig
    *   the Config state which may have been produced from parsing the arguments.
    * @param effects
    *   the effects produced from parsing the arguments.
    * @param onMissingConfig
    *   the throwable to use if no Config state was produced.
    * @return
    *   the final Config state after applying the effects.
    */
  def handler(
      maybeConfig: Option[Config],
      effects: List[OEffect],
      onMissingConfig: => Throwable
  ): IO[Config] =
    for {
      config <- IO.fromOption(maybeConfig)(onMissingConfig)
      ref <- Ref[IO].of(false)
      _ <- effectRunner(effects, onTerminate = ref.update(_ => true))
      terminated <- ref.get
    } yield
      if (terminated)
        config.modify(_.dupfind.action)(_ => Action.NoOp.toString)
      else
        config

  /** Loads initial config instance from default config file. Then applies override settings using given command-line
    * args.
    * @param args
    *   the command-line arguments being applied
    * @param program
    *   used by scopt for parsing arguments
    * @param version
    *   used by scopt for parsing arguments
    * @return
    */
  def apply(
      args: Seq[String],
      program: String,
      version: String
  ): IO[Config] =
    for {
      baseConfig <- IO(ConfigSource.default.loadOrThrow[Config])
      oParser = parser(program, version, baseConfig)
      (maybeConfig, effects) = OParser.runParser(oParser, args, baseConfig)
      finalConfig <- handler(maybeConfig, effects, new IllegalArgumentException(s"invalid args: $args"))
    } yield finalConfig
}
