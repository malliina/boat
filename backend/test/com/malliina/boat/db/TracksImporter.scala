package com.malliina.boat.db

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.malliina.boat.parsing.{BoatParser, FullCoord}
import com.malliina.boat.{BoatName, BoatUser, DateVal, DeviceId, InsertedPoint, KeyedSentence, LocalConf, RawSentence, SentencesEvent, TrackId, TrackInput, TrackNames}
import com.malliina.util.FileUtils
import com.malliina.values.Username
import tests.{AsyncSuite, DockerDatabase, TestConf}

import scala.concurrent.Future
import scala.concurrent.duration.DurationLong
import scala.jdk.CollectionConverters.CollectionHasAsScala

class TracksImporter extends AsyncSuite with DockerDatabase {
  lazy val c = Conf.fromConf(LocalConf.localConf).toOption.get

  ignore("import tracks from plotter log file") {
    val db = testDatabase(as, TestConf(container))
    val tdb = NewTracksDatabase(db)
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
  }

  ignore("modify tracks") {
    val oldTrack = TrackId(175)
    splitTracksByDate(
      oldTrack,
      NewTracksDatabase(BoatDatabase.withMigrations(as, TestConf(container)))
    )
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

  def splitTracksByDate(oldTrack: TrackId, db: NewTracksDatabase) = {
//    import db._
    import db.db._

    def createAndUpdateTrack(date: DateVal): IO[RunActionResult, Effect.Write] = {
      val in = TrackInput.empty(TrackNames.random(), DeviceId(14))
      for {
        newTrack <- runIO(db.tracksInsert(lift(in)))
        updated <- runIO(
          quote {
            rawPointsTable
              .filter(p => p.track == lift(oldTrack) && (dateOf(p.boatTime) == lift(date)))
              .update(_.track -> lift(newTrack))
          }
        )
      } yield updated
    }

    val action = for {
      dates <- runIO(
        pointsTable
          .filter(_.track == lift(oldTrack))
          .map(_.date)
          .distinct
      )
      updates <- IO.traverse(dates)(date => createAndUpdateTrack(date))
    } yield updates
    performIO(action)
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
