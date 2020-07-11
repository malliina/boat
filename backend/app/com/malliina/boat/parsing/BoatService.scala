package com.malliina.boat.parsing

import akka.Done
import akka.actor.ActorSystem
import akka.stream.{KillSwitches, Materializer}
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import com.malliina.boat.ais.AISSource
import com.malliina.boat.db.{TrackInsertsDatabase, TracksSource}
import com.malliina.boat.parsing.BoatService.log
import com.malliina.boat.{BoatEvent, BoatJsonError, CoordsEvent, FrontEvent, InsertedPoint, SentencesMessage, Streams, TimeFormatter, VesselMessages}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object BoatService {
  private val log = Logger(getClass)

  def apply(
    aisClient: AISSource,
    db: TrackInsertsDatabase,
    as: ActorSystem,
    mat: Materializer
  ): BoatService =
    new BoatService(aisClient, db)(as, mat)
}

class BoatService(aisClient: AISSource, db: TrackInsertsDatabase)(
  implicit as: ActorSystem,
  mat: Materializer
) extends Streams {
  implicit val ec: ExecutionContext = mat.executionContext
  val killSwitch = KillSwitches.shared("boats-switch")
  // Publish-Subscribe Akka Streams
  // https://doc.akka.io/docs/akka/2.5/stream/stream-dynamic.html
  val (boatSink, viewerSrc) = MergeHub
    .source[BoatEvent](perProducerBufferSize = 16)
    .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
    .run()
  val viewerSource = viewerSrc.via(killSwitch.flow)
  private val _ = viewerSource.runWith(Sink.ignore)
  private val sentencesSource = viewerSource.map { boatEvent =>
    BoatParser
      .read[SentencesMessage](boatEvent.message)
      .map(_.toTrackEvent(boatEvent.from.short))
      .left
      .map(err => BoatJsonError(err, boatEvent))
  }
  private val sentences = rights(sentencesSource)
  private val savedSentences =
    monitored(
      onlyOnce(sentences.mapAsync(parallelism = 1)(ss => db.saveSentences(ss))),
      "saved sentences"
    )
  private val __ = savedSentences.runWith(Sink.ignore)

  private val parsedSentences =
    monitored(
      savedSentences
        .mapConcat[ParsedSentence](e => BoatParser.parseMulti(e).toList),
      "parsed sentences"
    )
  parsedSentences.runWith(Sink.ignore)
  private val parsedEvents: Source[FullCoord, Future[Done]] =
    parsedSentences.via(BoatParser.multiFlow())
  private val savedCoords = monitored(
    onlyOnce(parsedEvents.mapAsync(parallelism = 1)(ce => saveRecovered(ce))),
    "saved coords"
  )
  private val coordsDrainer = savedCoords.runWith(Sink.ignore)
  private val errors = lefts(sentencesSource)
  private val ais = monitored(onlyOnce(aisClient.slow), "AIS messages")
  ais.runWith(Sink.ignore)
  errors.runWith(
    Sink.foreach(err => log.error(s"JSON error for '${err.boat}': '${err.error}'."))
  )

  /** Location updates of boats (Boat-Tracker) and vessels (AIS).
    *
    * @param formatter time formatter
    * @return location updates of boats (Boat-Tracker) and vessels (AIS).
    */
  def clientEvents(formatter: TimeFormatter): Source[FrontEvent, Future[Done]] =
    savedCoords
      .mapConcat[CoordsEvent](_.map { ip =>
        CoordsEvent(
          List(ip.coord.timed(ip.inserted.point, formatter)),
          ip.inserted.track.strip(formatter)
        )
      })
      .merge(ais.map(pairs => VesselMessages(pairs.map(_.toInfo(formatter)))))

  private def saveRecovered(coord: FullCoord): Future[List[Inserted]] =
    db.saveCoords(coord).map { inserted => List(Inserted(coord, inserted)) }.recover {
      case t =>
        log.error(s"Unable to save coords.", t)
        Nil
    }

  def close(): Unit = killSwitch.shutdown()
}

case class Inserted(coord: FullCoord, inserted: InsertedPoint)
