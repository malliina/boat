package com.malliina.boat.db

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, ZoneOffset}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.malliina.boat.parsing.{BoatParser, FullCoord}
import com.malliina.boat.{LocalConf, BoatId, BoatName, BoatUser, KeyedSentence, RawSentence, SentencesEvent, TrackId, TrackInput, TrackNames, TrackPointId, UserToken}
import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.file.FileUtilities
import com.malliina.measure.Distance
import com.malliina.values.Username
import play.api.Mode
import tests.BaseSuite

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class TracksDatabaseTests extends BaseSuite {
  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()

  ignore("init tokens") {
    val (db, _) = initDb()
    import db.api._
    import db.usersTable

    val action = for {
      users <- usersTable.result
      ts <- DBIO.sequence(users.map(u => usersTable.filter(_.id === u.id).map(_.token).update(UserToken.random())))
    } yield ts
    await(db.run(action))
  }

  ignore("modify tracks") {
    val db = BoatSchema(DatabaseConf(Mode.Prod, LocalConf.localConf))
    val oldTrack = TrackId(175)
    splitTracksByDate(oldTrack, db)
  }

  ignore("sentences") {
    val (db, tdb) = initDb()
    import db._
    import db.api._

    //    val tracks = await(db.run(sentencesTable.filter(t => !t.track.inSet(Set(TrackId(65), TrackId(66)))).map(_.track).distinct.result))
    //    tracks.foreach { t =>
    //      await(parseAndInsert(t), 300.seconds)
    //    }

    await(parseAndInsert(TrackId(167)), 300.seconds)

    def parseAndInsert(track: TrackId) = {
      val sentences = db.run {
        for {
          from <- db.first(tracksView.filter(_.track === track), s"Track not found: '$track'.")
          ss <- sentencesTable.filter(_.track === track).result
          keyed = ss.map(s => KeyedSentence(s.id, s.sentence, from.strip(Distance.zero)))
        } yield keyed
      }
      val src = Source.fromFuture(sentences).flatMapConcat(ss => Source(ss.toList))
      src.via(insertPointsFlow(tdb.saveCoords)).runWith(Sink.ignore)
    }
  }

  ignore("from file") {
    val (db, tdb) = initDb()
    val trackName = TrackNames.random()
    val track = await(tdb.join(BoatUser(trackName, BoatName("Amina"), Username("mle"))), 10.seconds)
    println(s"Using $track")
    val s: Source[RawSentence, NotUsed] = fromFile(FileUtilities.userHome.resolve(".boat/Log.txt"))
    val events = s.map(s => SentencesEvent(Seq(s), track.strip(Distance.zero)))
    val task = events.via(processSentences(tdb.saveSentences, tdb.saveCoords)).runWith(Sink.ignore)
    await(task, 30000.seconds)
    splitTracksByDate(track.track, db)
  }

  def fromFile(file: Path): Source[RawSentence, NotUsed] =
    Source(Files.readAllLines(file, StandardCharsets.UTF_8).asScala.map(RawSentence.apply).toList)

  def initDb() = {
    val db = BoatSchema(DatabaseConf(Mode.Prod, LocalConf.localConf))
    db.init()
    val tdb = TracksDatabase(db, mat.executionContext)
    (db, tdb)
  }

  def processSentences(saveSentences: SentencesEvent => Future[Seq[KeyedSentence]],
                       saveCoord: FullCoord => Future[Seq[TrackPointId]]) =
    Flow[SentencesEvent]
      .via(Flow[SentencesEvent].mapAsync(1)(saveSentences))
      .mapConcat(saved => saved.toList)
      .via(insertPointsFlow(saveCoord))

  def insertPointsFlow(save: FullCoord => Future[Seq[TrackPointId]]): Flow[KeyedSentence, Seq[TrackPointId], NotUsed] = {
    Flow[KeyedSentence]
      .mapConcat(raw => BoatParser.parse(raw).toOption.toList)
      .via(BoatParser.multiFlow())
      .via(Flow[FullCoord].mapAsync(1)(save))
  }

  def splitTracksByDate(oldTrack: TrackId, db: BoatSchema) = {
    import db._
    import db.api._

    def createAndUpdateTrack(date: LocalDate) =
      for {
        newTrack <- trackInserts += TrackInput(TrackNames.random(), BoatId(14))
        updated <- updateTrack(oldTrack, date, newTrack)
      } yield updated

    def updateTrack(track: TrackId, date: LocalDate, newTrack: TrackId) =
      pointsTable.map(_.combined)
        .filter((t: LiftedCoord) => t.track === track && t.date === date)
        .map(_.track)
        .update(newTrack)

    val action = for {
      dates <- pointsTable.filter(_.track === oldTrack).map(_.combined.date).distinct.sorted.result
      updates <- DBIO.sequence(dates.map(date => createAndUpdateTrack(date)))
    } yield updates
    await(db.run(action), 100.seconds)
  }
}
