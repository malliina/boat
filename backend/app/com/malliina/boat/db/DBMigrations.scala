package com.malliina.boat.db

import org.flywaydb.core.Flyway

object DBMigrations {
  def run(conf: Conf): Unit = {
    val flyway = Flyway
      .configure()
      .dataSource(conf.url, conf.user, conf.pass)
      .baselineOnMigrate(true)
      .baselineVersion("9")
      .load()
    if (flyway.info().current() == null) {
      flyway.baseline()
    }
    flyway.migrate()
  }
}
