package com.malliina.boat.db

import com.malliina.boat.LocalConf
import play.api.Mode
import tests.BaseSuite

abstract class DatabaseSuite extends BaseSuite {
  def initDb() = {
    val db = BoatSchema(DatabaseConf(Mode.Prod, LocalConf.localConf))
    db.init()
    db
  }
}
