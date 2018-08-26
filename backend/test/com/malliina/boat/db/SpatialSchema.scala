package com.malliina.boat.db

import com.malliina.boat.Coord
import com.malliina.boat.db.BoatSchema.NumThreads
import com.malliina.concurrent.Execution.cached
import com.vividsolutions.jts.geom.Point
import javax.sql.DataSource
import slick.jdbc.{GetResult, H2Profile, PositionedResult}

case class CoordRow(id: Int, coord: Coord)

class SpatialSchema(ds: DataSource, conf: ProfileConf)
  extends DatabaseLike(conf.profile, conf.profile.api.Database.forDataSource(ds, Option(NumThreads), BoatSchema.executor(NumThreads))) {

  object api extends Mappings(impl) with impl.API

  import api._

  val coordsTable = TableQuery[SpatialCoordsTable]
  val lastId = SimpleFunction.nullary[Long](conf.lastIdFunc)

  object GetDummy extends GetResult[Int] {
    override def apply(v1: PositionedResult) = 0
  }

  override def init(): Unit = {
    super.init()
    if (conf.profile == H2Profile) {
      val clazz = "org.h2gis.functions.factory.H2GISFunctions.load"
      val a = for {
        _ <- sqlu"""CREATE ALIAS IF NOT EXISTS H2GIS_SPATIAL FOR "#$clazz";"""
        _ <- sql"CALL H2GIS_SPATIAL();".as[Int](GetDummy)
      } yield 42
      runAndAwait(a)
      println("Initialized h2gis")
    }
  }

  override def tableQueries = Seq(coordsTable)

  val distanceSphere = SimpleFunction.binary[Point, Point, Double]("ST_Distance_Sphere")
  val distanceFunc = impl match {
    case H2Profile => "ST_MaxDistance"
    case _ => "ST_Distance_Sphere"
  }
  val distanceCoords = SimpleFunction.binary[Coord, Coord, Double](distanceFunc)

  class SpatialCoordsTable(tag: Tag) extends Table[CoordRow](tag, "spatial_coords3") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)

    def coord = column[Coord]("coord")

    def * = (id, coord) <> ((CoordRow.apply _).tupled, CoordRow.unapply)
  }

}
