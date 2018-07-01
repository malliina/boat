package com.malliina.boat.parsing

import java.time.LocalDate

import akka.actor.Status.{Failure, Success}
import akka.actor.{Actor, ActorRef, Props}
import com.malliina.boat.TrackId
import com.malliina.boat.parsing.ProcessorActor.log
import com.malliina.measure.{Speed, Temperature}
import play.api.Logger

object ProcessorActor {
  private val log = Logger(getClass)

  def props(processed: ActorRef) = Props(new ProcessorActor(processed))
}

/** Combines various NMEA0183 events, emits complete events of type `FullCoord` to `processed`.
  */
class ProcessorActor(processed: ActorRef) extends Actor {
  var latestDepth: Map[TrackId, WaterDepth] = Map.empty
  var latestWaterTemp: Map[TrackId, Temperature] = Map.empty
  var latestWaterSpeed: Map[TrackId, Speed] = Map.empty
  var latestBoatSpeed: Map[TrackId, Speed] = Map.empty
  // latest suitable date, if any
  var latestDate: Map[TrackId, LocalDate] = Map.empty
  // buffer for coords without a suitable date
  var coords: Map[TrackId, Seq[ParsedCoord]] = Map.empty

  override def receive: Receive = {
    case ParsedDate(date, from) =>
      val track = from.track
      latestDate = latestDate.updated(track, date)
      emitIfComplete(track)
    case ParsedBoatSpeed(knots, from) =>
      val track = from.track
      latestBoatSpeed = latestBoatSpeed.updated(track, knots)
      emitIfComplete(track)
    case ParsedWaterSpeed(knots, from) =>
      // does not seem to contain data, let's ignore it for now
//      latestWaterSpeed = latestWaterSpeed.updated(from.track, knots)
    case wd@WaterDepth(_, _, from) =>
      val track = from.track
      latestDepth = latestDepth.updated(track, wd)
      emitIfComplete(track)
    case WaterTemperature(celcius, from) =>
      val track = from.track
      latestWaterTemp = latestWaterTemp.updated(track, celcius)
      emitIfComplete(track)
    case coord@ParsedCoord(_, _, from) =>
      val track = from.track
      val emitted = emitIfComplete(track, Seq(coord))
      if (!emitted) {
        coords = coords.updated(track, coords.getOrElse(track, Nil) :+ coord)
      }
    case Success(s) =>
      log.warn(s"Stream completed with message '$s'.")
    case Failure(t) =>
      log.error(s"Stream completed with failure.", t)
  }

  def emitIfComplete(track: TrackId): Unit = {
    val emitted = emitIfComplete(track, coords.getOrElse(track, Nil))
    if (emitted) {
      coords = coords.updated(track, Nil)
    }
  }

  /**
    * @return true if complete, false otherwise
    */
  def emitIfComplete(track: TrackId, buffer: Seq[ParsedCoord]): Boolean =
    (for {
      date <- latestDate.get(track)
      speed <- latestBoatSpeed.get(track)
      temp <- latestWaterTemp.get(track)
      depth <- latestDepth.get(track)
    } yield {
      buffer.foreach(coord => processed ! coord.complete(date, speed, temp, depth.depth, depth.offset))
      true
    }).getOrElse(false)
}
