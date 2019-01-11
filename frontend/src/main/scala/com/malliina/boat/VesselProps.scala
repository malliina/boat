package com.malliina.boat

import play.api.libs.json.Json

/** JSON model for GeoJSON properties of each AIS vessel.
  *
  * Use simple JSON primitives only as values (no objects), nested objects do not seem to work.
  */
case class VesselProps(mmsi: Mmsi,
                       name: VesselName,
                       heading: Int)

object VesselProps {
  implicit val json = Json.format[VesselProps]
}
