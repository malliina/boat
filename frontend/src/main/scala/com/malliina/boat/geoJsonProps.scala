package com.malliina.boat

import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import io.circe.*
import io.circe.generic.semiauto.*

/** JSON model for GeoJSON properties of each AIS vessel.
  *
  * Use simple JSON primitives only as values (no objects), nested objects do not seem to work.
  */
case class VesselProps(mmsi: Mmsi, name: VesselName, heading: Int) derives Codec.AsObject

case class PointProps(
  boatName: BoatName,
  trackName: TrackName,
  speed: SpeedM,
  altitude: Option[DistanceM],
  waterTemp: Temperature,
  outsideTemp: Option[Temperature],
  depth: DistanceM,
  battery: Option[Energy],
  dateTime: FormattedDateTime,
  sourceType: SourceType
) derives Codec.AsObject

object PointProps:
  def apply(c: TimedCoord, ref: TrackRef): PointProps =
    PointProps(
      ref.boatName,
      ref.trackName,
      c.speed,
      c.altitude,
      c.waterTemp,
      c.outsideTemp,
      c.depthMeters,
      c.battery,
      c.time.dateTime,
      ref.sourceType
    )

case class DeviceProps(
  deviceName: BoatName,
  lng: Longitude,
  lat: Latitude,
  dateTime: FormattedDateTime
) derives Codec.AsObject:
  def coord = Coord(lng, lat)
