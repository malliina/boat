package com.malliina.boat.db

import org.flywaydb.core.Flyway

object DBMigrations {
  def run(conf: DatabaseConf): Unit = {
    val flyway = new Flyway()
    flyway.setDataSource(conf.url, conf.user, conf.pass)
    flyway.setBaselineOnMigrate(true)
    flyway.setBaselineVersionAsString("2")
    if (flyway.info().current() == null) {
      flyway.baseline()
    }
    flyway.migrate()
  }
}
