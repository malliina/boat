package com.malliina.boat.db

import com.malliina.boat.{Coord, CoordHash, FairwayLighting, Latitude, Longitude, SeaArea, ShowableLong}
import com.malliina.measure.DistanceM

opaque type FairwayId = Long
object FairwayId extends ShowableLong[FairwayId]:
  override def apply(raw: Long): FairwayId = raw
  override def write(t: FairwayId): Long = t

opaque type FairwayCoordId = Long
object FairwayCoordId extends ShowableLong[FairwayCoordId]:
  override def apply(raw: Long): FairwayCoordId = raw
  override def write(t: FairwayCoordId): Long = t

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
