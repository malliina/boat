package com.malliina.boat

import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import play.api.libs.json.Json

/** JSON model for GeoJSON properties of each AIS vessel.
  *
  * Use simple JSON primitives only as values (no objects), nested objects do not seem to work.
  */
case class VesselProps(mmsi: Mmsi, name: VesselName, heading: Int)

object VesselProps {
  implicit val json = Json.format[VesselProps]
}

case class PointProps(boatName: BoatName,
                      trackName: TrackName,
                      speed: SpeedM,
                      waterTemp: Temperature,
                      depth: DistanceM,
                      dateTime: FormattedDateTime)

object PointProps {
  implicit val json = Json.format[PointProps]

  def apply(c: TimedCoord, ref: TrackRef): PointProps =
    PointProps(ref.boatName, ref.trackName, c.speed, c.waterTemp, c.depthMeters, c.time.dateTime)
}
