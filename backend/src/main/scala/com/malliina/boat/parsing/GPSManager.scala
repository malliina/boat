package com.malliina.boat.parsing

import com.malliina.boat.DeviceId

object GPSManager:
  def apply(): GPSManager = new GPSManager()

class GPSManager extends SentenceAggregator[DeviceId, ParsedGPSCoord, GPSCoord]:
  private var latestDateTime: Map[DeviceId, ParsedGPSDateTime] = Map.empty
  private var latestSatellites: Map[DeviceId, SatellitesInView] = Map.empty
  private var latestFix: Map[DeviceId, GPSInfo] = Map.empty

  def update(event: ParsedGPSSentence): List[GPSCoord] = event match
    case dateTime @ ParsedGPSDateTime(_, _, _) =>
      val boat = dateTime.boat
      latestDateTime = latestDateTime.updated(boat, dateTime)
      completed(boat)
    case siv @ SatellitesInView(_, sentence) =>
      val boat = sentence.from
      latestSatellites = latestSatellites.updated(boat, siv)
      completed(boat)
    case gps @ GPSInfo(_, _, sentence) =>
      val boat = sentence.from
      latestFix = latestFix.updated(boat, gps)
      completed(boat)
    case coord @ ParsedGPSCoord(_, _, _) =>
      val boat = coord.boat
      val emittable = complete(coord.boat, List(coord))
      if emittable.isEmpty then coords = coords.updated(boat, coords.getOrElse(boat, Nil) :+ coord)
      emittable

  /** @return
    *   true if complete, false otherwise
    */
  override def complete(boat: DeviceId, buffer: List[ParsedGPSCoord]): List[GPSCoord] =
    (for
      dateTime <- latestDateTime.get(boat)
      gps <- latestFix.get(boat)
      satellites <- latestSatellites.get(boat)
    yield buffer.map { coord =>
      val keys = Seq(coord.key, dateTime.key, gps.key, satellites.key)
      coord.complete(dateTime.date, dateTime.time, satellites.satellites, gps.fix, keys)
    }).getOrElse { Nil }
