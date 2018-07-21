package com.malliina.boat.parsing

import java.time.{LocalDate, LocalTime}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import com.malliina.boat._
import com.malliina.measure.{DistanceDouble, SpeedDouble, TemperatureDouble}
import net.sf.marineapi.nmea.parser.DataNotAvailableException
import net.sf.marineapi.nmea.sentence._
import net.sf.marineapi.nmea.util.{Time => NMEATime}
import play.api.Logger
import play.api.libs.json.{JsError, JsValue, Reads}

object BoatParser {
  private val log = Logger(getClass)

  val parser = DefaultParser()

  def multi(src: Source[ParsedSentence, NotUsed])(implicit as: ActorSystem, mat: Materializer) =
    src.via(multiFlow())

  def multiFlow()(implicit as: ActorSystem, mat: Materializer): Flow[ParsedSentence, FullCoord, NotUsed] =
    Streams.connected[ParsedSentence, FullCoord](dest => ProcessorActor.props(dest), as)

  def read[T: Reads](json: JsValue): Either[JsError, T] =
    json.validate[T].asEither.left.map(JsError.apply)

  def parseMulti(sentences: Seq[KeyedSentence]): Seq[ParsedSentence] =
    sentences.map(parse).flatMap(e => e.asOption(handleError))

  def readSentences(event: BoatEvent) =
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
  def parse(sentence: KeyedSentence): Either[SentenceError, ParsedSentence] =
    parser.parse(sentence.sentence).flatMap { parsed =>
      val id = parsed.getSentenceId
      try {
        if (id == SentenceId.GGA.name()) {
          val gga = parsed.asInstanceOf[GGASentence]
          val pos = gga.getPosition
          Right(ParsedCoord(Coord(pos.getLongitude, pos.getLatitude), toLocalTime(gga.getTime), sentence))
        } else if (id == SentenceId.ZDA.name()) {
          val zda = parsed.asInstanceOf[ZDASentence]
          val date = zda.getDate
          val time = zda.getTime
          if (isSuspect(time)) Left(SuspectTime(sentence.sentence))
          else Right(ParsedDateTime(LocalDate.of(date.getYear, date.getMonth, date.getDay), toLocalTime(time), sentence))
        } else if (id == SentenceId.VTG.name()) {
          val vtg = parsed.asInstanceOf[VTGSentence]
          Right(ParsedBoatSpeed(vtg.getSpeedKnots.knots, sentence))
        } else if (id == SentenceId.MTW.name()) {
          val mtw = parsed.asInstanceOf[MTWSentence]
          Right(WaterTemperature(mtw.getTemperature.celsius, sentence))
        } else if (id == SentenceId.DPT.name()) {
          val dpt = parsed.asInstanceOf[DPTSentence]
          Right(WaterDepth(dpt.getDepth.meters, dpt.getOffset.meters, sentence))
        } else {
          Left(UnknownSentence(sentence.sentence, s"Unsupported sentence: '$sentence'."))
        }
      } catch {
        case dnee: DataNotAvailableException =>
          Left(MissingData(sentence.sentence, dnee))
        case e: Exception =>
          Left(SentenceFailure(sentence.sentence, e))
      }
    }

  def toLocalTime(time: NMEATime): LocalTime =
    LocalTime.of(time.getHour, time.getMinutes, time.getSeconds.toInt)

  def isSuspect(time: NMEATime): Boolean =
    time.getHour == 0 && time.getMinutes == 0 && (time.getSeconds >= 0 && time.getSeconds <= 15)

  def handleError(err: SentenceError): Unit =
    err match {
      case UnknownSentence(_, message) =>
        log.debug(message)
      case st@SuspectTime(_) =>
        log.warn(st.message)
      case SentenceFailure(_, ex) =>
        log.error(err.message, ex)
      case _ =>
        log.error(err.message)
    }
}
