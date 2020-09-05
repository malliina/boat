package com.malliina.boat.db

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import play.api.Logger

object BoatDatabase {
  private val log = Logger(getClass)

  def migrate(conf: Conf) = {
    val flyway =
      Flyway.configure
        .dataSource(conf.url, conf.user, conf.pass)
        .table("flyway_schema_history2")
        .load()
    flyway.migrate()
  }

  def newDataSource(conf: Conf): HikariDataSource = {
    migrate(conf)
    Conf.dataSource(conf)
  }

  def fail(message: String): Nothing = throw new Exception(message)
}
