package com.malliina.boat.parsing

import akka.Done
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import com.malliina.boat.ais.BoatMqttClient
import com.malliina.boat.db.TracksSource
import com.malliina.boat.parsing.BoatService.log
import com.malliina.boat.{BoatEvent, BoatJsonError, CoordsEvent, SentencesMessage, Streams}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object BoatService {
  private val log = Logger(getClass)

  def apply(aisClient: BoatMqttClient, db: TracksSource, as: ActorSystem, mat: Materializer): BoatService =
    new BoatService(aisClient, db)(as, mat)
}

class BoatService(aisClient: BoatMqttClient, db: TracksSource)(implicit as: ActorSystem, mat: Materializer)
    extends Streams {
  implicit val ec: ExecutionContext = mat.executionContext
  // Publish-Subscribe Akka Streams
  // https://doc.akka.io/docs/akka/2.5/stream/stream-dynamic.html
  val (boatSink, viewerSource) = MergeHub
    .source[BoatEvent](perProducerBufferSize = 16)
    .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
    .run()
  val _ = viewerSource.runWith(Sink.ignore)
  val sentencesSource = viewerSource.map { boatEvent =>
    BoatParser
      .read[SentencesMessage](boatEvent.message)
      .map(_.toEvent(boatEvent.from.short))
      .left
      .map(err => BoatJsonError(err, boatEvent))
  }
  val sentences = rights(sentencesSource)
  val savedSentences =
    monitored(onlyOnce(sentences.mapAsync(parallelism = 1)(ss => db.saveSentences(ss))), "saved sentences")
  val sentencesDrainer = savedSentences.runWith(Sink.ignore)

  val parsedSentences =
    monitored(savedSentences.mapConcat[ParsedSentence](e => BoatParser.parseMulti(e).toList), "parsed sentences")
  parsedSentences.runWith(Sink.ignore)
  val parsedEvents: Source[FullCoord, Future[Done]] = parsedSentences.via(BoatParser.multiFlow())
  val savedCoords = monitored(onlyOnce(parsedEvents.mapAsync(parallelism = 1)(ce => saveRecovered(ce))), "saved coords")
  val coordsDrainer = savedCoords.runWith(Sink.ignore)
  val errors = lefts(sentencesSource)
  val frontEvents: Source[CoordsEvent, Future[Done]] = savedCoords.mapConcat[CoordsEvent](identity)
  val ais = monitored(onlyOnce(aisClient.slow), "AIS messages")
  ais.runWith(Sink.ignore)
  errors.runWith(Sink.foreach(err => log.error(s"JSON error for '${err.boat}': '${err.error}'.")))
  val allEvents = frontEvents.merge(ais)

  private def monitored[In, Mat](src: Source[In, Mat], label: String): Source[In, Future[Done]] =
    src.watchTermination()(Keep.right).mapMaterializedValue { done =>
      done.transform { tryDone =>
        tryDone.fold(
          t => log.error(s"Error in flow '$label'.", t),
          _ => log.warn(s"Flow '$label' completed.")
        )
        tryDone
      }
    }

  private def saveRecovered(coord: FullCoord): Future[List[CoordsEvent]] =
    db.saveCoords(coord)
      .map { inserted =>
        List(CoordsEvent(Seq(coord.timed(inserted.point)), inserted.track))
      }
      .recover {
        case t =>
          log.error(s"Unable to save coords.", t)
          Nil
      }
}
