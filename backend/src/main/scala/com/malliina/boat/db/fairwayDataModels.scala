package com.malliina.boat.db

import com.malliina.boat.{FairwayLighting, SeaArea}
import com.malliina.geo.{Coord, CoordHash, Latitude, Longitude}
import com.malliina.measure.DistanceM
import com.malliina.values.{ErrorMessage, ValidatedLong}

opaque type FairwayId = Long
object FairwayId extends ValidatedLong[FairwayId]:
  override def build(input: Long): Either[ErrorMessage, FairwayId] = Right(input)
  override def write(t: FairwayId): Long = t

opaque type FairwayCoordId = Long
object FairwayCoordId extends ValidatedLong[FairwayCoordId]:
  override def build(input: Long): Either[ErrorMessage, FairwayCoordId] = Right(input)
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
