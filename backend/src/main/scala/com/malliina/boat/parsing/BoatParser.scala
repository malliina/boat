package com.malliina.boat.parsing

import com.malliina.boat.*
import com.malliina.util.AppLogger
import io.circe.{Decoder, DecodingFailure, Json}

object BoatParser:
  private val log = AppLogger(getClass)

  def read[T: Decoder](json: Json): Either[DecodingFailure, T] =
    json.as[T]

  def parseMulti(sentences: Seq[KeyedSentence]): Seq[ParsedSentence] =
    sentences.map(parse).flatMap(e => e.asOption(handleError))

  def readSentences(event: BoatEvent): Either[DecodingFailure, SentencesEvent] =
    event.message.as[SentencesEvent]

  /** Parses the following values from NNEA 0183 sentences:
    *
    * <ul> <li>GGA: Coordinates</li> <li>ZDA: Time and date</li> <li>VTG: Boat speed</li> <li>MTW:
    * Water temperature</li> <li>DPT: Water depth</li> </ul>
    *
    * Both GGA and ZDA sentences contain the time (but not the date). We use the time value in ZDA
    * sentences and ignore the GGA time value, because ZDA also contains the date which is missing
    * from GGA, and the time in GGA sentences is sometimes incorrectly 000000.
    *
    * @param sentence
    *   NMEA 0183 sentence
    * @return
    */
  def parse(sentence: KeyedSentence): Either[SentenceError, ParsedSentence] =
    val raw = sentence.sentence
    SentenceParser.parse(raw).flatMap {
      case zda @ ZDAMessage(_, _, _, _, _, _, _) =>
        if zda.isSuspect then Left(SuspectTime(raw))
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

  private def handleError(err: SentenceError): Unit =
    err match
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
