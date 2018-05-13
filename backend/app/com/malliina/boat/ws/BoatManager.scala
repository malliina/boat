package com.malliina.boat.ws

import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.stream.Materializer
import com.malliina.boat._
import com.malliina.boat.parsing.{DefaultParser, SentenceError, SentenceException, UnknownSentence}
import com.malliina.boat.ws.BoatManager.{BoatMessage, NewBoat, log}
import com.malliina.boat.ws.ViewerManager.BoatUpdate
import com.malliina.play.models.Username
import net.sf.marineapi.nmea.sentence.{GGASentence, SentenceId}
import play.api.Logger
import play.api.libs.json.{JsError, JsValue, Json}

case class Boat(out: ActorRef, user: Username, trackName: String)

object BoatManager {
  private val log = Logger(getClass)

  def props(viewerManager: ActorRef, mat: Materializer) =
    Props(new BoatManager(viewerManager)(mat))

  case class NewBoat(boat: Boat)

  case class BoatMessage(message: JsValue, from: Username)

  object BoatMessage {
    val Event = "event"
    val Body = "body"
  }

}

class BoatManager(viewerManager: ActorRef)(implicit mat: Materializer) extends Actor {
  val parser = DefaultParser()

  var boats: Set[Boat] = Set.empty

  override def receive: Receive = {
    case NewBoat(boat) =>
      context watch boat.out
      boats += boat
      log.info(s"Boat '${boat.user}' connected.")
    case BoatMessage(message, from) =>
      log.info(s"Boat '$from' says '$message'.")
      handleBoatMessage(message, from)
    case Terminated(out) =>
      boats.find(_.out == out) foreach { boat =>
        boats -= boat
        log.info(s"Boat '${boat.user}' disconnected.")
      }
  }

  def handleBoatMessage(message: JsValue, from: Username) = {
    viewerManager ! BoatUpdate(message, from)
    val parsed = (message \ BoatMessage.Event).validate[String].flatMap {
      case Sentences.Key => (message \ BoatMessage.Body).validate[Sentences]
      case other => JsError(s"Unknown boat event: '$other'.")
    }
    parsed.map { ss =>
      handleSentences(ss.sentences, from)
    }.recover { case err =>
      log.error(s"Failed to parse message. '$err'")
    }
  }

  def handleSentences(ss: Seq[RawSentence], from: Username): Unit = {
    val coords: Seq[Coord] = ss.map(parse).flatMap(e => e.asOption(handleError))
    viewerManager ! BoatUpdate(Json.toJson(CoordsEvent(coords)), from)
  }

  def handleError(err: SentenceError): Unit =
    err match {
      case SentenceException(_, ex) =>
        log.error(err.message, ex)
      case _ =>
        log.error(err.message)
    }

  def parse(sentence: RawSentence): Either[SentenceError, Coord] = {
    parser.parse(sentence).flatMap { parsed =>
      if (parsed.getSentenceId == SentenceId.GGA.name()) {
        val gga = parsed.asInstanceOf[GGASentence]
        val pos = gga.getPosition
        //        val time = gga.getTime
        //        val localTime = LocalTime.of(time.getHour, time.getMinutes, time.getSeconds.toInt)
        Right(Coord(pos.getLongitude, pos.getLatitude))
      } else {
        Left(UnknownSentence(sentence, "Unsupported sentence."))
      }
    }
  }
}
