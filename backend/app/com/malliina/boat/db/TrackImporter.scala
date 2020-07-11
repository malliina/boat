package com.malliina.boat.db

import java.nio.file.Path

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Flow, Framing, Sink, Source}
import akka.util.ByteString
import com.malliina.boat.db.TrackImporter.log
import com.malliina.boat.parsing.{BoatParser, FullCoord}
import com.malliina.boat.{InsertedPoint, KeyedSentence, RawSentence, SentencesEvent, TrackMetaShort}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object TrackImporter {
  private val log = Logger(getClass)

  def apply(inserts: TrackInsertsDatabase, as: ActorSystem, ec: ExecutionContext): TrackImporter =
    new TrackImporter(inserts)(as, ec)
}

class TrackImporter(inserts: TrackInsertsDatabase)(implicit as: ActorSystem, ec: ExecutionContext) {

  /** Saves sentences in `file` to the database `track`.
    *
    * @param file NMEA sentence log
    * @param track target track
    * @return number of points saved
    */
  def save(file: Path, track: TrackMetaShort): Future[Long] = saveSource(fileSource(file), track)

  def fileSource(file: Path) = FileIO
    .fromPath(file)
    .via(Framing.delimiter(ByteString("\r\n"), 128))
    .map(bs => RawSentence(bs.utf8String))

  def saveSource(
    source: Source[RawSentence, Future[IOResult]],
    track: TrackMetaShort
  ): Future[Long] = {
    val describe = s"track ${track.trackName} with boat ${track.boatName} by ${track.username}"
    log.info(s"Saving sentences to $describe...")
    val events: Source[SentencesEvent, Future[IOResult]] =
      source
        .filter(_ != RawSentence.initialZda)
        .map(s => SentencesEvent(Seq(s), track))
        .watchTermination() {
          case (io, _) =>
            io.map { res =>
              log.info(s"Read ${res.count} bytes using $describe.")
              res
            }.recoverWith {
              case e =>
                log.error(s"Failed to read sentences using $describe.", e)
                Future.failed(e)
            }
        }
    val logger = Sink.fold[Long, InsertedPoint](0L) { (acc, _) =>
      if (acc % 100 == 0) {
        log.info(s"Inserted $acc items to $describe...")
      }
      acc + 1
    }
    events
      .via(processSentences)
      .runWith(logger)
  }

  def processSentences =
    Flow[SentencesEvent]
      .via(Flow[SentencesEvent].mapAsync(1)(inserts.saveSentences))
      .mapConcat(saved => saved.toList)
      .via(insertPointsFlow)

  def insertPointsFlow: Flow[KeyedSentence, InsertedPoint, NotUsed] = {
    Flow[KeyedSentence]
      .mapConcat(raw => BoatParser.parse(raw).toOption.toList)
      .via(BoatParser.multiFlow())
      .via(Flow[FullCoord].mapAsync(1)(inserts.saveCoords))
  }
}
