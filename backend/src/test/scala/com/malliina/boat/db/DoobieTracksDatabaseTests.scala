package com.malliina.boat.db

import com.malliina.boat.http.{Limits, SortOrder, TrackQuery, TrackSort, TracksQuery}
import com.malliina.boat.{Coord, Lang, Language, SimpleUserInfo}
import com.malliina.database.Conf
import com.malliina.measure.{DistanceIntM, DistanceM}
import com.malliina.values.{Username, lat, lng, lngLat}
import doobie.implicits.toSqlInterpolator
import tests.{MUnitDatabaseSuite, MUnitSuite}

class DoobieTracksDatabaseTests extends MUnitSuite with MUnitDatabaseSuite:
  dbFixture.test("run doobie query"): doobie =>
    val service = DoobieTracksDatabase(doobie)
    val res = service.hm.unsafeRunSync()
    assertEquals(res, None)

class DoobieTests extends MUnitSuite with Mappings:
  val conf = Conf(
    "jdbc:mysql://localhost:3306/boat",
    "changeme",
    "changeme",
    Conf.MySQLDriver,
    maxPoolSize = 5,
    autoMigrate = true,
    schemaTable = "flyway_schema_history2"
  )

  val dbResource = databaseFixture(conf)

  dbResource.test("make query".ignore): doobie =>
    val db = DoobieTracksDatabase(doobie)
    val task = db.tracksBundle(
      SimpleUserInfo(Username("mle"), Language.english, Nil),
      TracksQuery(Nil, TrackQuery(TrackSort.TopSpeed, SortOrder.Desc, Limits(10, 0))),
      Lang.default
    )
    val _ = task.unsafeRunSync()

  dbResource.test("measure distance".ignore): doobie =>
    import Mappings.given
    val db = DoobieTracksDatabase(doobie)
    val c1 = 60.0 lngLat 30.0
    val c2 = 70.0 lngLat 13.0
    val task = db.run:
      sql"select st_distance_sphere($c1, $c2)".query[DistanceM].unique
    val distance = task.unsafeRunSync()
    assert(distance > 2100.km)
    assert(distance < 2200.km)
