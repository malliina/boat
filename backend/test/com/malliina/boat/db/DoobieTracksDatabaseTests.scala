package com.malliina.boat.db

import tests.{AsyncSuite, DockerDatabase, TestConf}

class DoobieTracksDatabaseTests extends AsyncSuite with DockerDatabase {
  test("run doobie query") {
    val conf = TestConf(db())
    val ds = BoatDatabase.newDataSource(conf)
    val service = DoobieTracksDatabase(ds, dbExecutor)
    val res = await(service.hm)
    assert(res == 42)
    ds.close()
  }
}

class DoobieTests extends AsyncSuite {
  test("make query".ignore) {
    val conf =
      Conf(
        "jdbc:mysql://localhost:3306/boat?useSSL=false",
        "changeme",
        "changeme",
        Conf.MySQLDriver
      )
    val db = DoobieTracksDatabase(BoatDatabase.newDataSource(conf), dbExecutor)
    val res = await(db.test)
    res foreach println
    db.ds.close()
  }
}
