package com.malliina.boat.db

import com.malliina.boat.{BoatIdCompanion, Coord, CoordHash, FairwayLighting, Latitude, Longitude, SeaArea}
import com.malliina.measure.DistanceM
import com.malliina.values.{IdCompanion, WrappedId}

case class FairwayId(id: Long) extends AnyVal with WrappedId
object FairwayId extends BoatIdCompanion[FairwayId]

case class FairwayCoordId(id: Long) extends AnyVal with WrappedId
object FairwayCoordId extends IdCompanion[FairwayCoordId]

case class FairwayRow(
  id: FairwayId,
  nameFi: Option[String],
  nameSe: Option[String],
  start: Option[String],
  end: Option[String],
  depth: Option[DistanceM],
  depth2: Option[DistanceM],
  depth3: Option[DistanceM],
  lighting: FairwayLighting,
  classText: String,
  seaArea: SeaArea,
  state: Double
)

case class FairwayCoord(
  id: FairwayCoordId,
  coord: Coord,
  latitude: Latitude,
  longitude: Longitude,
  coordHash: CoordHash,
  fairway: FairwayId
)

case class FairwayCoordInput(
  coord: Coord,
  latitude: Latitude,
  longitude: Longitude,
  coordHash: CoordHash,
  fairway: FairwayId
)
