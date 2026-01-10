package com.malliina.boat.db

import cats.effect.IO
import com.malliina.boat.db.TestData.{london, sanfran}
import com.malliina.boat.parsing.{BoatStats, FullCoord}
import com.malliina.boat.{BoatNames, BoatUser, Coord, DeviceId, Language, MUnitDatabaseSuite, MUnitSuite, SourceType, TrackId, TrackMetaShort, TrackNames, TrackRef, UserToken, UserUtils}
import com.malliina.measure.{DistanceIntM, SpeedIntM, SpeedM, Temperature}
import com.malliina.values.{Email, RefreshToken, Username, ua}

import java.time.{LocalDate, LocalTime}

object TestData:
  val london = Coord.build(0.13, 51.5).toOption.get
  val sanfran = Coord.build(-122.4, 37.8).toOption.get

class TracksDatabaseTests extends MUnitSuite with MUnitDatabaseSuite:
  dbFixture.test("insertion of token"): db =>
    val users = DoobieUserManager(db)
    val email = Email.unsafe("santa@example.com")
    val action = for
      u <- users.register(email)
      res <- users.save(RefreshToken.unsafe("test-token"), RefreshService.SIWA, u.id)
    yield res
    action.unsafeRunSync()

  dbFixture.test("inserts update track aggregates"): db =>
    val tdb = DoobieTracksDatabase(db)
    val inserts = TrackInserter(db)
    val users = DoobieUserManager(db)
    val user = users.userInfo(Email.unsafe("aggregate@example.com")).unsafeRunSync()
    val boat = user.boats.head
    val bid = boat.id
    val action: IO[TrackRef] = for
      result <- inserts.joinAsSource(
        BoatUser(TrackNames.random(), boat.name, SourceType.Boat, user.username, Language.default)
      )
      t = result.track
      tid = t.track
      _ <- inserts.saveCoords(fakeCoord(london, 10.kmh, tid, bid))
      _ <- inserts.saveCoords(fakeCoord(sanfran, 20.kmh, tid, bid))
      track <- tdb.ref(t.trackName)
      _ <- users.deleteUser(user.username)
    yield track
    val t = action.unsafeRunSync()
    assertEquals(t.points, 2)
    assert(t.avgSpeed.exists(s => s > 14.kmh && s < 16.kmh))

  dbFixture.test("add comments to track"): db =>
    val tdb = TrackInserter(db)
    val udb = DoobieUserManager(db)
    val testComment = "test"
    val userInput =
      NewUser(
        UserUtils.random(),
        None,
        UserToken.random(),
        enabled = true
      )
    val trackName = TrackNames.random()
    val task = for
      u <- udb.addUser(userInput)
      uid = u.toOption.get.id
      t <- tdb
        .joinAsSource(
          BoatUser(
            trackName,
            BoatNames.random(),
            SourceType.Boat,
            u.toOption.get.user,
            Language.default
          )
        )
        .map(_.track)
      _ <- tdb.saveCoords(fakeCoord(london, 10.kmh, t.track, t.boat))
      t <- tdb.updateComments(t.track, testComment, uid)
    yield t.comments
    val dbComment = task.unsafeRunSync()
    assert(dbComment.contains(testComment))

  def fakeCoord(c: Coord, speed: SpeedM, track: TrackId, boat: DeviceId) =
    FullCoord(
      c,
      LocalTime.now(),
      LocalDate.now(),
      speed,
      BoatStats(
        Temperature.zeroCelsius,
        1.meters,
        0.meters
      ),
      TrackMetaShort(
        track,
        TrackNames.random(),
        boat,
        BoatNames.random(),
        Username.unsafe("whatever")
      ),
      Option(ua"Boat-Tracker/Test")
    )
