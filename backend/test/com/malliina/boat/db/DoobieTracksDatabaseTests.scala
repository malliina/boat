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
