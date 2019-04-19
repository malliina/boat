package com.malliina.boat.db

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.LocalDate

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.malliina.boat.parsing.FullCoord
import com.malliina.boat.{BoatId, BoatName, BoatUser, InsertedPoint, KeyedSentence, LocalConf, RawSentence, SentencesEvent, TrackId, TrackInput, TrackNames}
import com.malliina.concurrent.Execution.cached
import com.malliina.util.FileUtils
import com.malliina.values.Username
import play.api.Mode

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.Future
import scala.concurrent.duration.DurationLong

class TracksImporter extends TracksTester {
  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()

  ignore("import tracks from plotter log file") {
    val (db, tdb) = initDb()
    val trackName = TrackNames.random()
    val track = await(tdb.join(BoatUser(trackName, BoatName("Amina"), Username("mle"))), 10.seconds)
    println(s"Using $track")
    val s: Source[RawSentence, NotUsed] = fromFile(FileUtils.userHome.resolve(".boat/Log2019START.txt"))
      .drop(1332126)
      .filter(_ != RawSentence.initialZda)
    val events = s.map(s => SentencesEvent(Seq(s), track.short))
    val task = events.via(processSentences(tdb.saveSentences, tdb.saveCoords)).runWith(Sink.ignore)
    await(task, 300000.seconds)
    splitTracksByDate(track.track, db)
  }

  ignore("modify tracks") {
    val db = BoatSchema(DatabaseConf(Mode.Prod, LocalConf.localConf))
    val oldTrack = TrackId(175)
    splitTracksByDate(oldTrack, db)
  }

  def fromFile(file: Path): Source[RawSentence, NotUsed] =
    Source(Files.readAllLines(file, StandardCharsets.UTF_8).asScala.map(RawSentence.apply).toList)

  def processSentences(saveSentences: SentencesEvent => Future[Seq[KeyedSentence]],
                       saveCoord: FullCoord => Future[InsertedPoint]) =
    Flow[SentencesEvent]
      .via(Flow[SentencesEvent].mapAsync(1)(saveSentences))
      .mapConcat(saved => saved.toList)
      .via(insertPointsFlow(saveCoord))

  def splitTracksByDate(oldTrack: TrackId, db: BoatSchema) = {
    import db._
    import db.api._

    def createAndUpdateTrack(date: LocalDate) =
      for {
        newTrack <- trackInserts += TrackInput.empty(TrackNames.random(), BoatId(14))
        updated <- updateTrack(oldTrack, date, newTrack)
      } yield updated

    def updateTrack(track: TrackId, date: LocalDate, newTrack: TrackId) =
      pointsTable
        .map(_.combined)
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
