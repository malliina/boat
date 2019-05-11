package com.malliina.boat.parsing

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import com.malliina.boat._
import play.api.Logger
import play.api.libs.json.{JsError, JsValue, Reads}

object BoatParser {
  private val log = Logger(getClass)

  def multi(src: Source[ParsedSentence, NotUsed])(implicit as: ActorSystem, mat: Materializer) =
    src.via(multiFlow())

  def multiFlow()(implicit as: ActorSystem,
                  mat: Materializer): Flow[ParsedSentence, FullCoord, NotUsed] =
    Streams.connected[ParsedSentence, FullCoord](dest => ProcessorActor.props(dest), as)

  def read[T: Reads](json: JsValue): Either[JsError, T] =
    json.validate[T].asEither.left.map(JsError.apply)

  def parseMulti(sentences: Seq[KeyedSentence]): Seq[ParsedSentence] =
    sentences.map(parse).flatMap(e => e.asOption(handleError))

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
    TalkedSentence.parse(raw).flatMap {
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
    }
  }

  def handleError(err: SentenceError): Unit =
    err match {
      case UnknownSentence(_, message) =>
        log.debug(message)
      case st @ SuspectTime(_) =>
        log.warn(st.message)
      case SentenceFailure(_, ex) =>
        log.error(err.message, ex)
      case _ =>
        log.error(err.message)
    }
}
