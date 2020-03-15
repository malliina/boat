package com.malliina.boat.db

import java.time.{LocalDate, LocalTime}

import com.malliina.boat._
import com.malliina.boat.db.TestData._
import com.malliina.boat.parsing.FullCoord
import com.malliina.measure.{DistanceIntM, SpeedIntM, SpeedM, Temperature}
import com.malliina.values.{Email, UserId, Username}
import tests.{AsyncSuite, DockerDatabase, TestConf}

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationLong}

object TestData {
  val london = Coord.build(0.13, 51.5).toOption.get
  val sanfran = Coord.build(-122.4, 37.8).toOption.get
}

class TracksDatabaseTests extends AsyncSuite with DockerDatabase {
  test("inserts update track aggregates") {
    val conf = TestConf(container)

    val newDb = BoatDatabase.withMigrations(as, conf)
    val tdb = NewTracksDatabase(newDb)
    val users = NewUserManager(newDb)
    val user = await(users.userInfo(Email("aggregate@example.com")))
    val uid = user.id
    val boat = user.boats.head
    val bid = boat.id
    val action = for {
      t: TrackMeta <- tdb.joinAsBoat(BoatUser(TrackNames.random(), boat.name, user.username))
      tid = t.track
      _ <- tdb.saveCoords(fakeCoord(london, 10.kmh, tid, bid, uid))
      _ <- tdb.saveCoords(fakeCoord(sanfran, 20.kmh, tid, bid, uid))
      track: TrackRef <- tdb.ref(t.trackName, Language.swedish)
      _ <- users.deleteUser(user.username)
    } yield track
    val t = await(action)
    assert(t.points === 2)
    assert(t.avgSpeed.exists(s => s > 14.kmh && s < 16.kmh))
  }

  test("add comments to track") {
    val conf = TestConf(container)

    val newDb = BoatDatabase.withMigrations(as, conf)
    val tdb = NewTracksDatabase(newDb)
    val udb = NewUserManager(newDb)
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
      _ <- tdb.saveCoords(fakeCoord(london, 10.kmh, t.track, t.boat, uid))
      t <- tdb.updateComments(t.track, testComment, uid)
    } yield t.comments
    val dbComment = await(task)
    assert(dbComment.contains(testComment))
  }

  def measured[T](f: => Future[T]): (T, Duration) = {
    val start = System.currentTimeMillis()
    val t = await(f)
    val end = System.currentTimeMillis()
    (t, (end - start).millis)
  }

  def fakeCoord(c: Coord, speed: SpeedM, track: TrackId, boat: DeviceId, user: UserId) = {
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
}
