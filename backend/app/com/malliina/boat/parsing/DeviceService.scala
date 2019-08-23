package com.malliina.boat.parsing

import akka.Done
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import com.malliina.boat.db.GPSDatabase
import com.malliina.boat.parsing.DeviceService.log
import com.malliina.boat.{
  DeviceEvent,
  DeviceJsonError,
  FrontEvent,
  GPSCoordsEvent,
  GPSInsertedPoint,
  SentencesMessage,
  Streams,
  TimeFormatter
}
import play.api.Logger

import scala.concurrent.Future

object DeviceService {
  private val log = Logger(getClass)

  def apply(db: GPSDatabase, as: ActorSystem, mat: Materializer): DeviceService =
    new DeviceService(db)(as, mat)
}

class DeviceService(val db: GPSDatabase)(implicit as: ActorSystem, mat: Materializer)
    extends Streams {
  implicit val ec = mat.executionContext

  val (deviceSink, viewerSource) = MergeHub
    .source[DeviceEvent](perProducerBufferSize = 16)
    .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
    .run()
  private val _ = viewerSource.runWith(Sink.ignore)

  private val sentencesSource = viewerSource.map { boatEvent =>
    BoatParser
      .read[SentencesMessage](boatEvent.message)
      .map(_.toGpsEvent(boatEvent.from))
      .left
      .map(err => DeviceJsonError(err, boatEvent))
  }
  private val sentences = rights(sentencesSource)
  private val savedSentences =
    monitored(onlyOnce(sentences.mapAsync(parallelism = 1)(ss => db.saveSentences(ss))),
              "saved GPS sentences")
  private val __ = savedSentences.runWith(Sink.ignore)

  private val parsedSentences =
    monitored(savedSentences.mapConcat[ParsedGPSSentence](e => BoatParser.parseMultiGps(e).toList),
              "parsed GPS sentences")
  parsedSentences.runWith(Sink.ignore)
  private val parsedEvents: Source[GPSCoord, Future[Done]] =
    parsedSentences.via(BoatParser.gpsFlow())
  private val savedCoords: Source[List[GPSInserted], Future[Done]] =
    monitored(onlyOnce(parsedEvents.mapAsync(parallelism = 1)(ce => {
      log.info(s"ce $ce")
      saveRecovered(ce)
    })), "saved GPS coords")
  private val coordsDrainer = savedCoords.runWith(Sink.ignore)
  private val errors = lefts(sentencesSource)
  errors.runWith(Sink.foreach(err => log.error(s"JSON error for '${err.boat}': '${err.error}'.")))

  def clientEvents(formatter: TimeFormatter): Source[FrontEvent, Future[Done]] =
    savedCoords.mapConcat { ps =>
      ps.map { point =>
        GPSCoordsEvent(List(point.coord.timed(point.inserted.point, formatter)),
                       point.inserted.from.strip)
      }
    }

  private def saveRecovered(coord: GPSCoord): Future[List[GPSInserted]] =
    db.saveCoords(coord)
      .map { inserted =>
        List(GPSInserted(coord, inserted))
      }
      .recover {
        case t =>
          log.error(s"Unable to save coords.", t)
          Nil
      }
}

case class GPSInserted(coord: GPSCoord, inserted: GPSInsertedPoint)
