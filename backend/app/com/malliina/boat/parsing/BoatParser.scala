package com.malliina.boat.parsing

import com.malliina.boat._
import net.sf.marineapi.nmea.sentence.{GGASentence, SentenceId}
import play.api.Logger
import play.api.libs.json.{JsError, JsValue, Reads}

object BoatParser {
  private val log = Logger(getClass)

  val parser = DefaultParser()

  def read[T: Reads](json: JsValue): Either[JsError, T] =
    json.validate[T].asEither.left.map(JsError.apply)

  def parseCoords(sentences: SentencesEvent): CoordsEvent = {
    val coords = sentences.sentences.map(parse).flatMap(e => e.asOption(handleError))
    CoordsEvent(coords, sentences.from)
  }

  def readSentences(event: BoatEvent) =
    read[SentencesEvent](event.message)

  def parse(sentence: RawSentence): Either[SentenceError, Coord] =
    parser.parse(sentence).flatMap { parsed =>
      if (parsed.getSentenceId == SentenceId.GGA.name()) {
        val gga = parsed.asInstanceOf[GGASentence]
        val pos = gga.getPosition
        //        val time = gga.getTime
        //        val localTime = LocalTime.of(time.getHour, time.getMinutes, time.getSeconds.toInt)
        Right(Coord(pos.getLongitude, pos.getLatitude))
      } else {
        Left(UnknownSentence(sentence, s"Unsupported sentence: '$sentence'."))
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
