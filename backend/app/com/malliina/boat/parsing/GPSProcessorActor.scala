package com.malliina.boat.parsing

import akka.actor.{Actor, ActorRef, Props}
import akka.actor.Status.{Failure, Success}
import com.malliina.boat.{BoatId, TrackId}
import play.api.Logger
import GPSProcessorActor.log

object GPSProcessorActor {
  private val log = Logger(getClass)

  def props(processed: ActorRef) = Props(new GPSProcessorActor(processed))
}

class GPSProcessorActor(processed: ActorRef) extends Actor {
  var coords: Map[BoatId, Seq[ParsedGPSCoord]] = Map.empty
  var latestDateTime: Map[BoatId, ParsedGPSDateTime] = Map.empty

  override def receive: Receive = {
    case pd @ ParsedGPSDateTime(_, _, _) =>
      val boat = pd.boat
      latestDateTime = latestDateTime.updated(boat, pd)
      emitIfComplete(boat)
    case coord @ ParsedGPSCoord(_, _, _) =>
      val boat = coord.boat
      val emitted = emitIfComplete(boat, Seq(coord))
      if (!emitted) {
        coords = coords.updated(boat, coords.getOrElse(boat, Nil) :+ coord)
      }
    case success @ Success(s) =>
      log.warn(s"Stream completed with message '$s'.")
      processed ! success
    case failure @ Failure(t) =>
      log.error(s"Stream completed with failure.", t)
      processed ! failure
  }

  def emitIfComplete(boat: BoatId): Unit = {
    val emitted = emitIfComplete(boat, coords.getOrElse(boat, Nil))
    if (emitted) {
      coords = coords.updated(boat, Nil)
    }
  }

  /**
    * @return true if complete, false otherwise
    */
  def emitIfComplete(boat: BoatId, buffer: Seq[ParsedGPSCoord]): Boolean =
    latestDateTime
      .get(boat)
      .fold(false) { dateTime =>
        buffer.foreach { coord =>
          val sentenceKeys = Seq(coord.key, dateTime.key)
          processed ! coord.complete(dateTime.date, dateTime.time, sentenceKeys)
        }
        true
      }
}
