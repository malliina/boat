package com.malliina.boat.parsing

import akka.actor.Status.{Failure, Success}
import akka.actor.{Actor, ActorRef, Props}
import com.malliina.boat.DeviceId
import com.malliina.boat.parsing.GPSProcessorActor.log
import play.api.Logger

object GPSProcessorActor {
  private val log = Logger(getClass)

  def props(processed: ActorRef) = Props(new GPSProcessorActor(processed))
}

class GPSProcessorActor(processed: ActorRef) extends Actor {
  var coords: Map[DeviceId, Seq[ParsedGPSCoord]] = Map.empty
  var latestDateTime: Map[DeviceId, ParsedGPSDateTime] = Map.empty
  var latestSatellites: Map[DeviceId, SatellitesInView] = Map.empty
  var latestFix: Map[DeviceId, GPSInfo] = Map.empty

  override def receive: Receive = {
    case dateTime @ ParsedGPSDateTime(_, _, _) =>
      val boat = dateTime.boat
      latestDateTime = latestDateTime.updated(boat, dateTime)
      emitIfComplete(boat)
    case coord @ ParsedGPSCoord(_, _, _) =>
      val boat = coord.boat
      val emitted = emitIfComplete(boat, Seq(coord))
      if (emitted.isEmpty) {
        coords = coords.updated(boat, coords.getOrElse(boat, Nil) :+ coord)
      }
    case siv @ SatellitesInView(_, sentence) =>
      val boat = sentence.from
      latestSatellites = latestSatellites.updated(boat, siv)
      emitIfComplete(boat)
    case gps @ GPSInfo(_, _, sentence) =>
      val boat = sentence.from
      latestFix = latestFix.updated(boat, gps)
      emitIfComplete(boat)
    case success @ Success(s) =>
      log.warn(s"Stream completed with message '$s'.")
      processed ! success
    case failure @ Failure(t) =>
      log.error(s"Stream completed with failure.", t)
      processed ! failure
  }

  def emitIfComplete(boat: DeviceId): Unit = {
    val emitted = emitIfComplete(boat, coords.getOrElse(boat, Nil))
    if (emitted.nonEmpty) {
      coords = coords.updated(boat, Nil)
    }
  }

  /**
    * @return true if complete, false otherwise
    */
  def emitIfComplete(boat: DeviceId, buffer: Seq[ParsedGPSCoord]): Seq[GPSCoord] =
    (for {
      dateTime <- latestDateTime.get(boat)
      gps <- latestFix.get(boat)
      satellites <- latestSatellites.get(boat)
    } yield {
      val coords = buffer.map { coord =>
        val keys = Seq(coord.key, dateTime.key, gps.key, satellites.key)
        coord.complete(dateTime.date, dateTime.time, satellites.satellites, gps.fix, keys)
      }
      coords.foreach { coord =>
        processed ! coord
      }
      coords
    }).getOrElse(Nil)
}
