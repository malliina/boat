package com.malliina.boat.parsing

import java.time.LocalDate

import akka.actor.{Actor, ActorRef, Props}
import com.malliina.boat.TrackId

object ProcessorActor {
  def props(processed: ActorRef) = Props(new ProcessorActor(processed))
}

/** Combines date events with coordinate events.
  */
class ProcessorActor(processed: ActorRef) extends Actor {
  // latest suitable date, if any
  var latestDate: Map[TrackId, LocalDate] = Map.empty
  // buffer for coords without a suitable date
  var coords: Map[TrackId, Seq[ParsedCoord]] = Map.empty

  override def receive: Receive = {
    case ParsedDate(date, from) =>
      val track = from.track
      latestDate = latestDate.updated(track, date)
      coords.getOrElse(track, Nil).foreach { coord =>
        processed ! coord.withDate(date)
      }
      coords = coords.updated(track, Nil)
    case coord@ParsedCoord(_, _, from) =>
      val track = from.track
      latestDate.get(track).map { date =>
        processed ! coord.withDate(date)
      }.getOrElse {
        coords = coords.updated(track, coords.getOrElse(track, Nil) :+ coord)
      }
  }
}
