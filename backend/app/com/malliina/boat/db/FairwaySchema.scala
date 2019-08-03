package com.malliina.boat.db

import com.malliina.boat.{Coord, CoordHash, FairwayInfo, FairwayLighting, Latitude, Longitude, SeaArea}
import com.malliina.measure.DistanceM

trait FairwaySchema extends Mappings with DatabaseClient { self: JdbcComponent =>
  import api._
  val fairwaysTable = TableQuery[FairwaysTable]
  val fairwayCoordsTable = TableQuery[FairwayCoordsTable]

  class FairwaysTable(tag: Tag) extends Table[FairwayRow](tag, "fairways") {
    def id = column[FairwayId]("id", O.PrimaryKey, O.AutoInc)
    def nameFi = column[Option[String]]("name_fi", O.Length(128))
    def nameSe = column[Option[String]]("name_se", O.Length(128))
    def start = column[Option[String]]("start", O.Length(128))
    def end = column[Option[String]]("end", O.Length(128))
    def depth = column[Option[DistanceM]]("depth")
    def depth2 = column[Option[DistanceM]]("depth2")
    def depth3 = column[Option[DistanceM]]("depth3")
    def lighting = column[FairwayLighting]("lighting")
    def classText = column[String]("class_text")
    def seaArea = column[SeaArea]("sea_area")
    def state = column[Double]("state")

    def forInserts = (nameFi,
      nameSe,
      start,
      end,
      depth,
      depth2,
      depth3,
      lighting,
      classText,
      seaArea,
      state) <> ((FairwayInfo.apply _).tupled, FairwayInfo.unapply)
    def * = (id,
      nameFi,
      nameSe,
      start,
      end,
      depth,
      depth2,
      depth3,
      lighting,
      classText,
      seaArea,
      state) <> ((FairwayRow.apply _).tupled, FairwayRow.unapply)
  }

  class FairwayCoordsTable(tag: Tag) extends Table[FairwayCoord](tag, "fairway_coords") {
    def id = column[FairwayCoordId]("id", O.PrimaryKey, O.AutoInc)
    def coord = column[Coord]("coord")
    def lat = column[Latitude]("latitude")
    def lng = column[Longitude]("longitude")
    def hash = column[CoordHash]("coord_hash", O.Length(191))
    def fairway = column[FairwayId]("fairway")

    def fairwayConstraint = foreignKey("fairway_coords_fairway_fk", fairway, fairwaysTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )
    def hashIdx = index("fairway_coords_coord_hash_idx", hash)

    def forInserts =
      (coord, lat, lng, hash, fairway) <> ((FairwayCoordInput.apply _).tupled, FairwayCoordInput.unapply)
    def * =
      (id, coord, lat, lng, hash, fairway) <> ((FairwayCoord.apply _).tupled, FairwayCoord.unapply)
  }
}
