package com.malliina.boat.db

import cats.effect.IO
import com.malliina.boat.db.TestData.{london, sanfran}
import com.malliina.boat.parsing.FullCoord
import com.malliina.boat.{BoatNames, BoatUser, Coord, DeviceId, Language, TrackId, TrackMetaShort, TrackNames, TrackRef, UserToken}
import com.malliina.measure.{DistanceIntM, SpeedIntM, SpeedM, Temperature}
import com.malliina.values.{Email, Username}
import tests.{MUnitDatabaseSuite, MUnitSuite}

import java.time.{LocalDate, LocalTime}

object TestData {
  val london = Coord.build(0.13, 51.5).toOption.get
  val sanfran = Coord.build(-122.4, 37.8).toOption.get
}

class TracksDatabaseTests extends MUnitSuite with MUnitDatabaseSuite {
  doobieDb.test("inserts update track aggregates") { resource =>
    val newDb = resource.resource
    val tdb = DoobieTracksDatabase(newDb)
    val inserts = DoobieTrackInserts(newDb)
    val users = DoobieUserManager(newDb)
    val user = users.userInfo(Email("aggregate@example.com")).unsafeRunSync()
    val boat = user.boats.head
    val bid = boat.id
    val action: IO[TrackRef] = for {
      t <- inserts.joinAsBoat(BoatUser(TrackNames.random(), boat.name, user.username))
      tid = t.track
      _ <- inserts.saveCoords(fakeCoord(london, 10.kmh, tid, bid))
      _ <- inserts.saveCoords(fakeCoord(sanfran, 20.kmh, tid, bid))
      track <- tdb.ref(t.trackName, Language.swedish)
      _ <- users.deleteUser(user.username)
    } yield track
    val t = action.unsafeRunSync()
    assertEquals(t.points, 2)
    assert(t.avgSpeed.exists(s => s > 14.kmh && s < 16.kmh))
  }

  doobieDb.test("add comments to track") { resource =>
    val newDb = resource.resource
    val tdb = DoobieTrackInserts(newDb)
    val udb = DoobieUserManager(newDb)
    val testComment = "test"
    val userInput =
      NewUser(
        Username("test-comments-user"),
        None,
        UserToken.random(),
        enabled = true
      )
    val trackName = TrackNames.random()
    val task = for {
      u <- udb.addUser(userInput)
      uid = u.toOption.get.id
      t <- tdb.joinAsBoat(
        BoatUser(trackName, BoatNames.random(), u.toOption.get.user)
      )
      _ <- tdb.saveCoords(fakeCoord(london, 10.kmh, t.track, t.boat))
      t <- tdb.updateComments(t.track, testComment, uid)
    } yield t.comments
    val dbComment = task.unsafeRunSync()
    assert(dbComment.contains(testComment))
  }

  def fakeCoord(c: Coord, speed: SpeedM, track: TrackId, boat: DeviceId) =
    FullCoord(
      c,
      LocalTime.now(),
      LocalDate.now(),
      speed,
      Temperature.zeroCelsius,
      1.meters,
      0.meters,
      TrackMetaShort(
        track,
        TrackNames.random(),
        boat,
        BoatNames.random(),
        Username("whatever")
      )
    )
}
