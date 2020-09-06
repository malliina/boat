package com.malliina.boat.db

import com.malliina.boat.{Coord, Lang, Language, SimpleUserInfo}
import com.malliina.boat.http.{Limits, SortOrder, TrackQuery, TrackSort}
import com.malliina.measure.DistanceM
import com.malliina.values.Username
import tests.{AsyncSuite, DockerDatabase}
import doobie._
import doobie.implicits._

class DoobieTracksDatabaseTests extends AsyncSuite with DockerDatabase {
  test("run doobie query") {
    val doobie = DoobieDatabase(BoatDatabase.newDataSource(db()), dbExecutor)
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
    try {
      val db = DoobieTracksDatabase(doobie)
      def test = db.tracksBundle(
        SimpleUserInfo(Username("mle"), Language.english),
        TrackQuery(TrackSort.TopSpeed, SortOrder.Desc, Limits(10, 0)),
        Lang.default
      )
      val res = await(test).tracks
      res foreach println
    } finally doobie.close()
  }

  test("measure distance".ignore) {
    import DoobieMappings.coordMeta
    val doobie = DoobieDatabase(BoatDatabase.newDataSource(conf), dbExecutor)
    try {
      val db = DoobieTracksDatabase(doobie)
      val c1 = Coord.buildOrFail(60, 30)
      val c2 = Coord.buildOrFail(70, 13)
      val task = db.run {
        sql"select st_distance_sphere($c1, $c2)".query[DistanceM].unique
      }
      val distance = await(task)
      println(distance)
    } finally doobie.close()

  }
}
