package com.malliina.boat.parsing

import akka.actor.Status.{Failure, Success}
import akka.actor.{Actor, ActorRef, Props}
import com.malliina.boat.TrackId
import com.malliina.boat.parsing.PlotterProcessorActor.log
import play.api.Logger

object PlotterProcessorActor {
  private val log = Logger(getClass)

  def props(processed: ActorRef) = Props(new PlotterProcessorActor(processed))
}

/** Combines various NMEA0183 events, emits complete events of type `FullCoord` to `processed`.
  */
class PlotterProcessorActor(processed: ActorRef) extends Actor {
  var latestDepth: Map[TrackId, WaterDepth] = Map.empty
  var latestWaterTemp: Map[TrackId, WaterTemperature] = Map.empty
  var latestWaterSpeed: Map[TrackId, ParsedWaterSpeed] = Map.empty
  var latestBoatSpeed: Map[TrackId, ParsedBoatSpeed] = Map.empty
  // latest suitable date, if any
  var latestDateTime: Map[TrackId, ParsedDateTime] = Map.empty
  // buffer for coords without a suitable date
  var coords: Map[TrackId, Seq[ParsedCoord]] = Map.empty

  override def receive: Receive = {
    case pd@ParsedDateTime(_, _, _) =>
      val track = pd.track
      latestDateTime = latestDateTime.updated(track, pd)
      emitIfComplete(track)
    case pbs@ParsedBoatSpeed(_, _) =>
      val track = pbs.track
      latestBoatSpeed = latestBoatSpeed.updated(track, pbs)
      emitIfComplete(track)
    case pws@ParsedWaterSpeed(_, _) =>
    // does not seem to contain data, let's ignore it for now
    //      latestWaterSpeed = latestWaterSpeed.updated(from.track, knots)
    case wd@WaterDepth(_, _, _) =>
      val track = wd.track
      latestDepth = latestDepth.updated(track, wd)
      emitIfComplete(track)
    case wt@WaterTemperature(_, _) =>
      val track = wt.track
      latestWaterTemp = latestWaterTemp.updated(track, wt)
      emitIfComplete(track)
    case coord@ParsedCoord(_, _, _) =>
      val track = coord.track
      val emitted = emitIfComplete(track, Seq(coord))
      if (!emitted) {
        coords = coords.updated(track, coords.getOrElse(track, Nil) :+ coord)
      }
    case success@Success(s) =>
      log.warn(s"Stream completed with message '$s'.")
      processed ! success
    case failure@Failure(t) =>
      log.error(s"Stream completed with failure.", t)
      processed ! failure
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
      dateTime <- latestDateTime.get(track)
      speed <- latestBoatSpeed.get(track)
      temp <- latestWaterTemp.get(track)
      depth <- latestDepth.get(track)
    } yield {
      buffer.foreach { coord =>
        val sentenceKeys = Seq(coord.key, dateTime.key, speed.key, temp.key, depth.key)
        processed ! coord.complete(dateTime.date, dateTime.time, speed.speed, temp.temp, depth.depth, depth.offset, sentenceKeys)
      }
      true
    }).getOrElse(false)
}
