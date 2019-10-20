package com.malliina.boat.db

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.malliina.boat.parsing.FullCoord
import com.malliina.boat.{BoatName, BoatUser, DateVal, DeviceId, InsertedPoint, KeyedSentence, LocalConf, RawSentence, SentencesEvent, TrackId, TrackInput, TrackNames}
import com.malliina.concurrent.Execution.cached
import com.malliina.util.FileUtils
import com.malliina.values.Username

import scala.concurrent.Future
import scala.concurrent.duration.DurationLong
import scala.jdk.CollectionConverters.CollectionHasAsScala

class TracksImporter extends TracksTester {
  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()

  lazy val c = Conf.fromConf(LocalConf.localConf).toOption.get

  ignore("import tracks from plotter log file") {
    val (db, tdb) = initDbAndTracks()
    val trackName = TrackNames.random()
    val track = await(
      tdb.joinAsBoat(BoatUser(trackName, BoatName("Amina"), Username("mle"))),
      10.seconds
    )
    println(s"Using $track")
    val s: Source[RawSentence, NotUsed] =
      fromFile(FileUtils.userHome.resolve("boat/logs/Log201910.txt"))
        .drop(1236503)
        .filter(_ != RawSentence.initialZda)
    val events = s.map(s => SentencesEvent(Seq(s), track.short))
    val task = events
      .via(processSentences(tdb.saveSentences, tdb.saveCoords))
      .runWith(Sink.ignore)
    await(task, 300000.seconds)
//    splitTracksByDate(track.track, db)
  }

  ignore("modify tracks") {
    val db = BoatSchema(Conf.dataSource(c), c.driver)
    val oldTrack = TrackId(175)
    splitTracksByDate(oldTrack, db)
  }

  def fromFile(file: Path): Source[RawSentence, NotUsed] =
    Source(
      Files
        .readAllLines(file, StandardCharsets.UTF_8)
        .asScala
        .map(RawSentence.apply)
        .toList
    )

  def processSentences(
      saveSentences: SentencesEvent => Future[Seq[KeyedSentence]],
      saveCoord: FullCoord => Future[InsertedPoint]
  ) =
    Flow[SentencesEvent]
      .via(Flow[SentencesEvent].mapAsync(1)(saveSentences))
      .mapConcat(saved => saved.toList)
      .via(insertPointsFlow(saveCoord))

  def splitTracksByDate(oldTrack: TrackId, db: BoatSchema) = {
    import db._
    import db.api._

    def createAndUpdateTrack(date: DateVal) =
      for {
        newTrack <- trackInserts += TrackInput.empty(
          TrackNames.random(),
          DeviceId(14)
        )
        updated <- updateTrack(oldTrack, date, newTrack)
      } yield updated

    def updateTrack(track: TrackId, date: DateVal, newTrack: TrackId) =
      pointsTable
        .map(_.combined)
        .filter((t: LiftedCoord) => t.track === track && t.date === date)
        .map(_.track)
        .update(newTrack)

    val action = for {
      dates <- pointsTable
        .filter(_.track === oldTrack)
        .map(_.combined.date)
        .distinct
        .sorted
        .result
      updates <- DBIO.sequence(dates.map(date => createAndUpdateTrack(date)))
    } yield updates
    await(db.run(action), 100.seconds)
  }
}
