package org.dupfind.cats.effect.testkit

import java.util.UUID

import com.softwaremill.quicklens.*
import munit.CatsEffectSuite
import org.dupfind.cats.effect.Config
import pureconfig.ConfigSource

trait ConfigFixture { self: CatsEffectSuite =>
  def originalConfig: Config =
    ConfigSource.default.loadOrThrow[Config]

  def newDbConfig: Config =
    originalConfig.modify(_.dupfind.db.url)(_ => s"jdbc:h2:mem:test_${UUID.randomUUID()};DB_CLOSE_DELAY=-1")

  val configFixture =
    FunFixture[Config](setup = _ => newDbConfig, teardown = _ => ())
}
