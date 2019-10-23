package com.malliina.boat.db

import java.time.{LocalDate, LocalTime}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.malliina.boat._
import com.malliina.boat.db.TestData._
import com.malliina.boat.http.BoatQuery
import com.malliina.boat.parsing.{BoatParser, FullCoord}
import com.malliina.concurrent.Execution.cached
import com.malliina.measure.{DistanceIntM, SpeedIntM, SpeedM, Temperature}
import com.malliina.values.{Email, UserId, Username}
import tests.LegacyDatabase

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationLong}

object TestData {
  val london = Coord.build(0.13, 51.5).toOption.get
  val sanfran = Coord.build(-122.4, 37.8).toOption.get
}

class TracksDatabaseTests extends TracksTester with LegacyDatabase {
  ignore("collect rows") {
    val db = boatSchema
    db.initApp()
    val tdb = TracksDatabase(db, mat.executionContext)
    val lande = TrackName("todo")
    val dbStart = System.currentTimeMillis()
    def history =
      tdb.historyRows(
        SimpleUserInfo(Username("mle"), Language.english),
        BoatQuery.tracks(Seq(lande))
      )
    val result = await(history)
    val dbEnd = System.currentTimeMillis()
    tdb.collectPointsClassic(result, Language.english)
    val end = System.currentTimeMillis()
    println(
      s"DB took ${dbEnd - dbStart} ms, collected in ${end - dbEnd} ms, total ${end - dbStart} ms."
    )
  }

  test("inserts update track aggregates") {
    val db = boatSchema
    db.initApp()
    val newDb = BoatDatabase(ds, mat.executionContext, isMariaDb = true)
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
    val db = boatSchema
    db.initApp()
    val tdb = TracksDatabase(db, mat.executionContext)
    val udb = DatabaseUserManager(db, mat.executionContext)
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

  ignore("init tokens") {
    val (db, _) = initDbAndTracks()
    import db._
    import db.api._

    val action = for {
      users <- usersTable.result
      ts <- DBIO.sequence(
        users.map(
          u =>
            usersTable
              .filter(_.id === u.id)
              .map(_.token)
              .update(UserToken.random())
        )
      )
    } yield ts
    await(db.run(action))
  }

  ignore("sentences") {
    val (db, tdb) = initDbAndTracks()
    import db._
    import db.api._

    await(parseAndInsert(TrackId(167)), 300.seconds)

    def parseAndInsert(track: TrackId) = {
      val sentences = db.run {
        for {
          from <- db.first(
            trackMetas.filter(_.track === track),
            s"Track not found: '$track'."
          )
          ss <- sentencesTable.filter(_.track === track).result
          keyed = ss.map(s => KeyedSentence(s.id, s.sentence, from.short))
        } yield keyed
      }
      val src =
        Source.fromFuture(sentences).flatMapConcat(ss => Source(ss.toList))
      src.via(insertPointsFlow(tdb.saveCoords)).runWith(Sink.ignore)
    }
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

abstract class TracksTester extends DatabaseSuite {
  def initDbAndTracks() = {
    val db = initDb()
    val tdb = TracksDatabase(db, cached)
    (db, tdb)
  }

  def insertPointsFlow(save: FullCoord => Future[InsertedPoint])(
      implicit as: ActorSystem,
      mat: Materializer
  ): Flow[KeyedSentence, InsertedPoint, NotUsed] = {
    Flow[KeyedSentence]
      .mapConcat(raw => BoatParser.parse(raw).toOption.toList)
      .via(BoatParser.multiFlow())
      .via(Flow[FullCoord].mapAsync(1)(save))
  }
}
