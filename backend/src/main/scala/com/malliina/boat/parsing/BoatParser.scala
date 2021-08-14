package com.malliina.boat.parsing

import com.malliina.boat._
import com.malliina.util.AppLogger
import play.api.libs.json.{JsError, JsValue, Reads}

object BoatParser {
  private val log = AppLogger(getClass)

  def read[T: Reads](json: JsValue): Either[JsError, T] =
    json.validate[T].asEither.left.map(JsError.apply)

  def parseMulti(sentences: Seq[KeyedSentence]): Seq[ParsedSentence] =
    sentences.map(parse).flatMap(e => e.asOption(handleError))

  def parseMultiGps(sentences: Seq[GPSKeyedSentence]): Seq[ParsedGPSSentence] =
    sentences.map(parseGps).flatMap(e => e.asOption(handleError))

  def readSentences(event: BoatEvent): Either[JsError, SentencesEvent] =
    read[SentencesEvent](event.message)

  /** Parses the following values from NNEA 0183 sentences:
    *
    * <ul>
    * <li>GGA: Coordinates</li>
    * <li>ZDA: Time and date</li>
    * <li>VTG: Boat speed</li>
    * <li>MTW: Water temperature</li>
    * <li>DPT: Water depth</li>
    * </ul>
    *
    * Both GGA and ZDA sentences contain the time (but not the date). We use the time value in ZDA sentences and ignore
    * the GGA time value, because ZDA also contains the date which is missing from GGA, and the time in GGA sentences
    * is sometimes incorrectly 000000.
    *
    * @param sentence NMEA 0183 sentence
    * @return
    */
  def parse(sentence: KeyedSentence): Either[SentenceError, ParsedSentence] = {
    val raw = sentence.sentence
    SentenceParser.parse(raw).flatMap {
      case zda @ ZDAMessage(_, _, _, _, _, _, _) =>
        if (zda.isSuspect) Left(SuspectTime(raw))
        else Right(ParsedDateTime(zda.date, zda.timeUtc, sentence))
      case GGAMessage(_, time, lat, lng, _, _, _, _, _, _) =>
        Right(ParsedCoord(Coord(lng.toDecimalDegrees, lat.toDecimalDegrees), time, sentence))
      case VTGMessage(_, _, _, speed, _) =>
        Right(ParsedBoatSpeed(speed, sentence))
      case MTWMessage(_, temperature) =>
        Right(WaterTemperature(temperature, sentence))
      case DPTMessage(_, depth, offset) =>
        Right(WaterDepth(depth, offset, sentence))
      case _ =>
        Left(IgnoredSentence(raw))
    }
  }

  def parseGps(sentence: GPSKeyedSentence): Either[SentenceError, ParsedGPSSentence] = {
    val raw = sentence.sentence
    SentenceParser.parse(raw).flatMap {
      case GGAMessage(_, time, lat, lng, _, _, _, _, _, _) =>
        Right(ParsedGPSCoord(Coord(lng.toDecimalDegrees, lat.toDecimalDegrees), time, sentence))
      case zda @ ZDAMessage(_, _, _, _, _, _, _) =>
        Right(ParsedGPSDateTime(zda.date, zda.timeUtc, sentence))
      case GSVMessage(_, satellites, _, _) =>
        Right(SatellitesInView(satellites, sentence))
      case GSAMessage(_, mode, fix) =>
        Right(GPSInfo(mode, fix, sentence))
      case RMCMessage(_, timeUtc, date, _, _) =>
        Right(ParsedGPSDateTime(date, timeUtc, sentence))
      case _ =>
        Left(IgnoredSentence(raw))
    }
  }

  def handleError(err: SentenceError): Unit =
    err match {
      case IgnoredSentence(_) =>
        log.debug(err.messageString)
      case UnknownSentence(_, message) =>
        log.debug(message)
      case SuspectTime(_) =>
        log.warn(err.messageString)
      case SentenceFailure(_, ex) =>
        log.error(err.messageString, ex)
      case InvalidSentence(sentence, message) =>
        log.warn(s"Invalid sentence '$sentence'. $message")
    }
}
