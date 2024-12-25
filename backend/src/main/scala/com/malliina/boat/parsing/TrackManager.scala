package com.malliina.boat.parsing

import com.malliina.boat.{TrackId, UserAgent}

import scala.annotation.unused

/** Combines various NMEA0183 events, returning any complete events of type `FullCoord`.
  */
object TrackManager:
  def apply(): TrackManager = new TrackManager

class TrackManager extends SentenceAggregator[TrackId, ParsedCoord, FullCoord]:
  private var latestDepth: Map[TrackId, WaterDepth] = Map.empty
  private var latestWaterTemp: Map[TrackId, WaterTemperature] = Map.empty
  @unused
  private var latestWaterSpeed: Map[TrackId, ParsedWaterSpeed] = Map.empty
  private var latestBoatSpeed: Map[TrackId, ParsedBoatSpeed] = Map.empty
  // latest suitable date, if any
  private var latestDateTime: Map[TrackId, ParsedDateTime] = Map.empty

  def update(event: ParsedSentence, userAgent: Option[UserAgent]): List[FullCoord] = event match
    case pd @ ParsedDateTime(_, _, _) =>
      val track = pd.track
      latestDateTime = latestDateTime.updated(track, pd)
      completed(track, userAgent)
    case pbs @ ParsedBoatSpeed(_, _) =>
      val track = pbs.track
      latestBoatSpeed = latestBoatSpeed.updated(track, pbs)
      completed(track, userAgent)
    case ParsedWaterSpeed(_, _) =>
      // does not seem to contain data, let's ignore it for now
      //      latestWaterSpeed = latestWaterSpeed.updated(from.track, knots)
      Nil
    case wd @ WaterDepth(_, _, _) =>
      val track = wd.track
      latestDepth = latestDepth.updated(track, wd)
      completed(track, userAgent)
    case wt @ WaterTemperature(_, _) =>
      val track = wt.track
      latestWaterTemp = latestWaterTemp.updated(track, wt)
      completed(track, userAgent)
    case coord @ ParsedCoord(_, _, _) =>
      val track = coord.track
      val emittable = complete(track, List(coord), userAgent)
      if emittable.isEmpty then
        coords = coords.updated(track, coords.getOrElse(track, Nil) :+ coord)
      emittable

  /** @return
    *   true if complete, false otherwise
    */
  override def complete(
    track: TrackId,
    buffer: List[ParsedCoord],
    userAgent: Option[UserAgent]
  ): List[FullCoord] =
    (for
      dateTime <- latestDateTime.get(track)
      speed <- latestBoatSpeed.get(track)
      temp <- latestWaterTemp.get(track)
      depth <- latestDepth.get(track)
    yield buffer.map: coord =>
      val sentenceKeys = List(coord.key, dateTime.key, speed.key, temp.key, depth.key)
      coord.complete(
        dateTime.date,
        dateTime.time,
        speed.speed,
        temp.temp,
        depth.depth,
        depth.offset,
        sentenceKeys,
        userAgent
      )
    ).getOrElse(Nil)
