package com.malliina.boat.db

import com.malliina.boat.{Lang, Language, SimpleUserInfo}
import com.malliina.boat.http.{Limits, SortOrder, TrackQuery, TrackSort}
import com.malliina.values.Username
import tests.{AsyncSuite, DockerDatabase, TestConf}

class DoobieTracksDatabaseTests extends AsyncSuite with DockerDatabase {
  test("run doobie query") {
    val conf = TestConf(db())
    val doobie = DoobieDatabase(BoatDatabase.newDataSource(conf), dbExecutor)
    val service = DoobieTracksDatabase(doobie)
    val res = await(service.hm)
    assert(res == 42)
    doobie.close()
  }
}

class DoobieTests extends AsyncSuite {
  val conf = Conf(
    "jdbc:mysql://localhost:3306/boat?useSSL=false",
    "changeme",
    "changeme",
    Conf.MySQLDriver
  )

  test("make query".ignore) {
    val doobie = DoobieDatabase(BoatDatabase.newDataSource(conf), dbExecutor)
    val db = DoobieTracksDatabase(doobie)
    def test = db.tracksBundle(
      SimpleUserInfo(Username("mle"), Language.english),
      TrackQuery(TrackSort.TopSpeed, SortOrder.Desc, Limits(10, 0)),
      Lang.default
    )
    val res = await(test).tracks
    res foreach println
    doobie.close()
  }
}
