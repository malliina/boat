package com.malliina.boat.db

import com.malliina.boat.http.{Limits, SortOrder, TrackQuery, TrackSort}
import com.malliina.boat.{Coord, Lang, Language, SimpleUserInfo}
import com.malliina.measure.{DistanceIntM, DistanceM}
import com.malliina.values.Username
import doobie.implicits.toSqlInterpolator
import tests.{MUnitDatabaseSuite, MUnitSuite}

class DoobieTracksDatabaseTests extends MUnitSuite with MUnitDatabaseSuite {
  doobieDb.test("run doobie query") { doobie =>
    val service = DoobieTracksDatabase(doobie.resource)
    val res = service.hm.unsafeRunSync()
    assertEquals(res, 42)
  }
}

class DoobieTests extends MUnitSuite {
  val conf = Conf(
    "jdbc:mysql://localhost:3306/boat?useSSL=false",
    "changeme",
    "changeme",
    Conf.MySQLDriver,
    maxPoolSize = 5
  )

  val dbResource = databaseFixture(conf)

  dbResource.test("make query".ignore) { dbr =>
    val doobie = dbr.resource
    val db = DoobieTracksDatabase(doobie)
    val task = db.tracksBundle(
      SimpleUserInfo(Username("mle"), Language.english, Nil),
      TrackQuery(TrackSort.TopSpeed, SortOrder.Desc, Limits(10, 0)),
      Lang.default
    )
    val bundle = task.unsafeRunSync()
  }

  dbResource.test("measure distance".ignore) { dbr =>
    implicit val coordMeta = DoobieMappings.coordMeta
    val doobie = dbr.resource
    val db = DoobieTracksDatabase(doobie)
    val c1 = Coord.buildOrFail(60, 30)
    val c2 = Coord.buildOrFail(70, 13)
    val task = db.run {
      sql"select st_distance_sphere($c1, $c2)".query[DistanceM].unique
    }
    val distance = task.unsafeRunSync()
    assert(distance > 2100.km)
    assert(distance < 2200.km)
  }
}
