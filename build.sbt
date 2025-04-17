ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.5"
ThisBuild / organization := "org.example"
ThisBuild / organizationName := "example"
ThisBuild / scalafixOnCompile := true
ThisBuild / semanticdbEnabled := true

val catsEffectVersion = "3.6.0"
val doobieVersion = "1.0.0-RC8"
val flywayVersion = "11.6.0"
val fs2Version = "3.12.0"
val monocleVersion = "3.3.0"

enablePlugins(BuildInfoPlugin)
buildInfoKeys := Seq(name, version, scalaVersion, sbtVersion)
buildInfoPackage := "org.dupfind.cats.effect.build"
buildInfoObject := "BuildInfo"

// Avoids assembly conflict on module-info.class from various libraries including:
// logback, jackson, hikari, slf4j
assembly / assemblyMergeStrategy := {
  case PathList("module-info.class") => MergeStrategy.discard
  case x if x.endsWith("/module-info.class") => MergeStrategy.discard
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}

// avoids: [WARNING] IOApp `main` is running on a thread other than the main thread.
Compile / run / fork := true

wartremoverErrors ++= Warts.unsafe

lazy val root = (project in file("."))
  .settings(
    name := "dupfind-cats-effect",
    assembly / mainClass := Some("org.dupfind.cats.effect.Dupfind"),
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.5.18",
      "co.fs2" %% "fs2-core" % fs2Version,
      "co.fs2" %% "fs2-io" % fs2Version,
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.8", // https://github.com/pureconfig/pureconfig
      "com.github.scopt" %% "scopt" % "4.1.0", // https://github.com/scopt/scopt
      "com.lihaoyi" %% "pprint" % "0.9.0",
      "com.softwaremill.quicklens" %% "quicklens" % "1.9.12",
      "org.flywaydb" % "flyway-core" % flywayVersion,
      "org.flywaydb" % "flyway-database-postgresql" % flywayVersion,
      "org.slf4j" % "slf4j-api" % "2.0.17",
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "org.tpolecat" %% "doobie-core" % doobieVersion,
      "org.tpolecat" %% "doobie-hikari" % doobieVersion,
      "org.tpolecat" %% "doobie-h2" % doobieVersion,
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,
      "org.typelevel" %% "log4cats-slf4j" % "2.7.0",
      "org.typelevel" %% "munit-cats-effect" % "2.1.0" % Test,
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    )
  )
