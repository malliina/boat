package com.malliina.boat.db

import com.malliina.boat.Coord
import com.malliina.boat.db.BoatSchema.NumThreads
import com.malliina.concurrent.Execution.cached
import com.vividsolutions.jts.geom.Point
import javax.sql.DataSource
import slick.jdbc.{GetResult, PositionedResult}

case class CoordRow(id: Int, coord: Coord)

class SpatialSchema(ds: DataSource, val jdbc: BoatJdbcProfile)
    extends DatabaseClient
    with Mappings {
  import jdbc.api._
  val database: jdbc.backend.DatabaseDef =
    jdbc.api.Database.forDataSource(ds, Option(NumThreads), BoatSchema.executor(NumThreads))

  val coordsTable = TableQuery[SpatialCoordsTable]
  val lastId = SimpleFunction.nullary[Long](jdbc.lastId)

  object GetDummy extends GetResult[Int] {
    override def apply(v1: PositionedResult) = 0
  }

  def init(): Unit = {
    initTablesIfRequired()
  }

  def initTablesIfRequired(): Unit = {
    createIfNotExists(tableQueries: _*)
  }

  def tableQueries = Seq(coordsTable)

  val distanceSphere = SimpleFunction.binary[Point, Point, Double]("ST_Distance_Sphere")
  val distanceCoords = SimpleFunction.binary[Coord, Coord, Double](jdbc.distance)

  class SpatialCoordsTable(tag: Tag) extends Table[CoordRow](tag, "spatial_coords3") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)

    def coord = column[Coord]("coord")

    def * = (id, coord) <> ((CoordRow.apply _).tupled, CoordRow.unapply)
  }

}
