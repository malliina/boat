package com.malliina.boat.db

import java.time.{LocalDate, LocalTime}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import com.malliina.boat._
import com.malliina.boat.db.TestData._
import com.malliina.boat.http.BoatQuery
import com.malliina.boat.parsing.{BoatParser, FullCoord}
import com.malliina.concurrent.Execution.cached
import com.malliina.measure.{DistanceIntM, SpeedM, SpeedIntM, Temperature}
import com.malliina.values.{UserId, Username}
import play.api.Mode
import tests.BaseSuite

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationLong}

object TestData {
  val london = Coord.build(0.13, 51.5).toOption.get
  val sanfran = Coord.build(-122.4, 37.8).toOption.get
}

class TracksDatabaseTests extends TracksTester {
  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()
//  val conf = DatabaseConf("jdbc:mysql://localhost:3306/boat?useSSL=false",
//                          "",
//                          "",
//                          DatabaseConf.MySQLDriver)
  val conf = DatabaseConf.inMemory

  ignore("collect rows") {
    val db = BoatSchema(conf)
    db.initApp()
    val tdb = TracksDatabase(db, mat.executionContext)
    val guettaName = TrackName("todo")
    def history =
      tdb.historyRows(SimpleUserInfo(Username("malliina123@gmail.com"), Language.english),
                      BoatQuery.tracks(Seq(guettaName)))
    val result = await(history)
    val start = System.currentTimeMillis()
    tdb.collectPointsClassic(result, Language.english)
    val end = System.currentTimeMillis()
    println(s"Done in ${end - start} ms")
  }

  ignore("performance") {
    val db = BoatSchema(conf)
    db.initApp()
    val tdb = TracksDatabase(db, mat.executionContext)
    val guettaName = TrackName("todo")
    def history = tdb.history(SimpleUserInfo(Username("malliina123@gmail.com"), Language.english),
                              BoatQuery.tracks(Seq(guettaName)))
    val result = await(history)
  }

  def measured[T](f: => Future[T]): (T, Duration) = {
    val start = System.currentTimeMillis()
    val t = await(f)
    val end = System.currentTimeMillis()
    (t, (end - start).millis)
  }

  def fakeCoord(c: Coord, speed: SpeedM, track: TrackId, boat: BoatId, user: UserId) = {
    FullCoord(
      c,
      LocalTime.now(),
      LocalDate.now(),
      speed,
      Temperature.zeroCelsius,
      1.meters,
      0.meters,
      TrackMetaShort(track, TrackNames.random(), boat, BoatNames.random(), Username("whatever"))
    )
  }

  test("inserts update track aggregates") {
    val db = BoatSchema(conf)
    db.initApp()
    val tdb = TracksDatabase(db, mat.executionContext)
    val user = NewUser(Username("test-agg-user"), None, UserToken.random(), enabled = true)

    import db._
    import db.api._
    val action = for {
      uid <- userInserts += user
      boat = BoatInput(BoatNames.random(), BoatTokens.random(), uid)
      bid <- boatInserts += boat
      tid: TrackId <- trackInserts += TrackInput.empty(TrackNames.random(), bid)
      _ <- tdb.saveCoordAction(fakeCoord(london, 10.kmh, tid, bid, uid))
      _ <- tdb.saveCoordAction(fakeCoord(sanfran, 20.kmh, tid, bid, uid))
      track: TrackRow <- first(tracksTable.filter(_.id === tid.bind), s"Track not found: '$tid'.")
      _ <- usersTable.filter(_.id === uid).delete
    } yield track
    val t = runAndAwait(action)
    assert(t.avgSpeed.exists(s => s > 14.kmh && s < 16.kmh))
    assert(t.points === 2)
  }

  test("add comments to track") {
    val db = BoatSchema(conf)
    db.initApp()
    val tdb = TracksDatabase(db, mat.executionContext)
    val udb = DatabaseUserManager(db, mat.executionContext)
    val testComment = "test"
    val userInput = NewUser(Username("test-comments-user"), None, UserToken.random(), enabled = true)
    val trackName = TrackNames.random()
    val task = for {
      u <- udb.addUser(userInput)
      uid = u.toOption.get.id
      t <- tdb.join(BoatUser(trackName, BoatNames.random(), u.toOption.get.username))
      _ <- tdb.saveCoords(fakeCoord(london, 10.kmh, t.track, t.boat, uid))
      t <- tdb.updateComments(t.track, testComment, uid)
    } yield t.comments
    val dbComment = await(task)
    assert(dbComment.contains(testComment))
  }

  ignore("init tokens") {
    val (db, _) = initDbAndTracks()
    import db.api._
    import db.usersTable

    val action = for {
      users <- usersTable.result
      ts <- DBIO.sequence(
        users.map(u => usersTable.filter(_.id === u.id).map(_.token).update(UserToken.random())))
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
          from <- db.first(trackMetas.filter(_.track === track), s"Track not found: '$track'.")
          ss <- sentencesTable.filter(_.track === track).result
          keyed = ss.map(s => KeyedSentence(s.id, s.sentence, from.short))
        } yield keyed
      }
      val src = Source.fromFuture(sentences).flatMapConcat(ss => Source(ss.toList))
      src.via(insertPointsFlow(tdb.saveCoords)).runWith(Sink.ignore)
    }
  }
}

abstract class TracksTester extends DatabaseSuite {
  def initDbAndTracks() = {
    val db = initDb()
    val tdb = TracksDatabase(db, cached)
    (db, tdb)
  }

  def insertPointsFlow(
                        save: FullCoord => Future[InsertedPoint])(implicit as: ActorSystem, mat: Materializer): Flow[KeyedSentence, InsertedPoint, NotUsed] = {
    Flow[KeyedSentence]
      .mapConcat(raw => BoatParser.parse(raw).toOption.toList)
      .via(BoatParser.multiFlow())
      .via(Flow[FullCoord].mapAsync(1)(save))
  }
}
