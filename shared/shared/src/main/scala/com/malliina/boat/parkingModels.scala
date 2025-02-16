package com.malliina.boat

import com.malliina.http.FullUrl
import com.malliina.measure.DistanceM
import io.circe.{Codec, Decoder}

case class ParkingCapacity(next: Option[FullUrl], features: Seq[Feature]) derives Codec.AsObject

case class CapacityProps(capacityEstimate: Option[Int])

object CapacityProps:
  private case class CapacityPropsJson(capacity_estimate: Option[Int]) derives Codec.AsObject

  given Decoder[CapacityProps] = Decoder[CapacityPropsJson].map: json =>
    CapacityProps(json.capacity_estimate)

case class NearestCoord(coord: Coord, distance: DistanceM) derives Codec.AsObject

case class ParkingDirections(from: Coord, to: Seq[Coord], nearest: NearestCoord, capacity: Int)
  derives Codec.AsObject

case class ParkingResponse(directions: Seq[ParkingDirections]) derives Codec.AsObject
