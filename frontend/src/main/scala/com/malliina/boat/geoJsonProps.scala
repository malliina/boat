package com.malliina.boat

import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import io.circe.*
import io.circe.generic.semiauto.*

/** JSON model for GeoJSON properties of each AIS vessel.
  *
  * Use simple JSON primitives only as values (no objects), nested objects do not seem to work.
  */
case class VesselProps(mmsi: Mmsi, name: VesselName, heading: Int)

object VesselProps:
  implicit val json: Codec[VesselProps] = deriveCodec[VesselProps]

case class PointProps(
  boatName: BoatName,
  trackName: TrackName,
  speed: SpeedM,
  waterTemp: Temperature,
  outsideTemp: Option[Temperature],
  depth: DistanceM,
  dateTime: FormattedDateTime,
  sourceType: SourceType
)

object PointProps:
  implicit val json: Codec[PointProps] = deriveCodec[PointProps]

  def apply(c: TimedCoord, ref: TrackRef): PointProps =
    PointProps(
      ref.boatName,
      ref.trackName,
      c.speed,
      c.waterTemp,
      c.outsideTemp,
      c.depthMeters,
      c.time.dateTime,
      ref.sourceType
    )

case class DeviceProps(
  deviceName: BoatName,
  lng: Longitude,
  lat: Latitude,
  dateTime: FormattedDateTime
):
  def coord = Coord(lng, lat)

object DeviceProps:
  implicit val json: Codec[DeviceProps] = deriveCodec[DeviceProps]

  def apply(c: GPSTimedCoord, ref: DeviceRef): DeviceProps =
    DeviceProps(ref.deviceName, c.coord.lng, c.coord.lat, c.time.dateTime)
