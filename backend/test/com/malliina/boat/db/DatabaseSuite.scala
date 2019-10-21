package com.malliina.boat.db

import com.malliina.boat.LocalConf
import tests.BaseSuite

abstract class DatabaseSuite extends BaseSuite {
  @deprecated("Use something else", "1.0")
  def initDb(): BoatSchema = {
    val c = Conf.fromConf(LocalConf.localConf).toOption.get
    val db = BoatSchema(Conf.dataSource(c), c.driver)
    db.init()
    db
  }
}
