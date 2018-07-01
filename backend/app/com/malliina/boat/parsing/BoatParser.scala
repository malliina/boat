package com.malliina.boat.parsing

import java.time.{LocalDate, LocalTime}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.malliina.boat._
import net.sf.marineapi.nmea.parser.DataNotAvailableException
import net.sf.marineapi.nmea.sentence._
import play.api.Logger
import play.api.libs.json.{JsError, JsValue, Reads}
import com.malliina.measure.{SpeedDouble, TemperatureDouble}

object BoatParser {
  private val log = Logger(getClass)

  val parser = DefaultParser()

  def multi(src: Source[ParsedSentence, NotUsed])(implicit as: ActorSystem, mat: Materializer) =
    src.via(multiFlow())

  def multiFlow()(implicit as: ActorSystem, mat: Materializer) =
    Streams.connected[ParsedSentence, FullCoord](dest => ProcessorActor.props(dest), as)

  def read[T: Reads](json: JsValue): Either[JsError, T] =
    json.validate[T].asEither.left.map(JsError.apply)

  def parseMulti(sentences: SentencesEvent) =
    sentences.sentences.map(s => parse(s, sentences.from)).flatMap(e => e.asOption(handleError))

  def readSentences(event: BoatEvent) =
    read[SentencesEvent](event.message)

  def parse(sentence: RawSentence, from: TrackRef): Either[SentenceError, ParsedSentence] =
    parser.parse(sentence).flatMap { parsed =>
      val id = parsed.getSentenceId
      try {
        if (id == SentenceId.GGA.name()) {
          val gga = parsed.asInstanceOf[GGASentence]
          val pos = gga.getPosition
          val time = gga.getTime
          val localTime = LocalTime.of(time.getHour, time.getMinutes, time.getSeconds.toInt)
          Right(ParsedCoord(Coord(pos.getLongitude, pos.getLatitude), localTime, from))
        } else if (id == SentenceId.ZDA.name()) {
          val zda = parsed.asInstanceOf[ZDASentence].getDate
          Right(ParsedDate(LocalDate.of(zda.getYear, zda.getMonth, zda.getDay), from))
        } else if (id == SentenceId.VTG.name()) {
          val vtg = parsed.asInstanceOf[VTGSentence]
          Right(ParsedBoatSpeed(vtg.getSpeedKnots.knots, from))
        } else if (id == SentenceId.VHW.name()) {
          val vhw = parsed.asInstanceOf[VHWSentence]
          Right(ParsedWaterSpeed(vhw.getSpeedKnots.knots, from))
        } else if (id == SentenceId.MTW.name()) {
          val mtw = parsed.asInstanceOf[MTWSentence]
          Right(WaterTemperature(mtw.getTemperature.celsius, from))
        } else {
          Left(UnknownSentence(sentence, s"Unsupported sentence: '$sentence'."))
        }
      } catch {
        case dnee: DataNotAvailableException =>
          Left(MissingData(sentence, dnee))
        case e: Exception =>
          Left(SentenceFailure(sentence, e))
      }
    }

  def handleError(err: SentenceError): Unit =
    err match {
      case UnknownSentence(_, message) =>
        log.debug(message)
      case SentenceFailure(_, ex) =>
        log.error(err.message, ex)
      case _ =>
        log.error(err.message)
    }
}
