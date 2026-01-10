package com.malliina.boat.db

import ch.qos.logback.classic.Level
import com.malliina.boat.*
import com.malliina.boat.http.*
import com.malliina.database.Conf
import com.malliina.http.UrlSyntax.url
import com.malliina.logback.LogbackUtils
import com.malliina.measure.{DistanceIntM, DistanceM}
import com.malliina.values.*
import doobie.implicits.toSqlInterpolator

class DoobieTracksDatabaseTests extends MUnitSuite with MUnitDatabaseSuite:
  LogbackUtils.init(rootLevel = Level.OFF)

  dbFixture.test("run doobie query"): doobie =>
    val service = DoobieTracksDatabase(doobie)
    val res = service.hm.unsafeRunSync()
    assertEquals(res, None)

class DoobieTests extends MUnitSuite with Mappings:
  val conf = Conf(
    url"jdbc:mariadb://localhost:3306/boat",
    "changeme",
    Password.unsafe("changeme"),
    BoatConf.mariaDbDriver,
    maxPoolSize = 5,
    autoMigrate = true,
    schemaTable = "flyway_schema_history2"
  )

  val dbResource = databaseFixture(conf)

  dbResource.test("make query".ignore): doobie =>
    val db = DoobieTracksDatabase(doobie)
    val task = db.tracksBundle(
      SimpleUserInfo(Username.unsafe("mle"), Language.English, Nil),
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
