package com.malliina.boat.db

import java.sql.{PreparedStatement, ResultSet}

import com.malliina.boat.Coord
import com.malliina.boat.db.BoatSchema.NumThreads
import com.malliina.boat.db.SpatialUtils._
import com.vividsolutions.jts.geom._
import com.vividsolutions.jts.geom.impl.CoordinateArraySequenceFactory
import com.vividsolutions.jts.io._
import javax.sql.DataSource
import slick.ast.FieldSymbol
import slick.jdbc.{JdbcProfile, PositionedParameters, SetParameter}
import tests.BaseSuite

import scala.reflect.ClassTag

class SpatialSlickTests extends BaseSuite {
  val gf = new GeometryFactory(new PrecisionModel(), 4326, CoordinateArraySequenceFactory.instance())
  val point = gf.createPoint(new Coordinate(-71.064544, 42.28787))

  case class SpatialRow(id: Int, lon: Double, lat: Double, p: Point)

  object SpatialRow {
    def forCoord(id: Int, c: Coord): SpatialRow =
      SpatialRow(id, c.lng, c.lat, gf.createPoint(new Coordinate(c.lng, c.lat)))
  }

  case class CoordRow(id: Int, coord: Coord)

  class SpatialSchema(ds: DataSource, override val impl: JdbcProfile)
    extends DatabaseLike(impl, impl.api.Database.forDataSource(ds, Option(NumThreads), BoatSchema.executor(NumThreads))) {

    object api extends impl.API

    import api._

    val spatialTable = TableQuery[SpatialTable]
    val coordsTable = TableQuery[SpatialCoordsTable]

    override def tableQueries = Seq(spatialTable, coordsTable)

    class GeometryJdbcType[T <: Geometry](implicit override val classTag: ClassTag[T]) extends impl.DriverJdbcType[T] {

      override def sqlType: Int = java.sql.Types.OTHER

      override def sqlTypeName(sym: Option[FieldSymbol]): String = "geometry"

      override def getValue(r: ResultSet, idx: Int): T = {
        val value = r.getBytes(idx)
        if (r.wasNull) null.asInstanceOf[T] else fromBytes[T](value)
      }

      override def setValue(v: T, p: PreparedStatement, idx: Int): Unit = {
        p.setBytes(idx, toBytes(v))
      }

      override def updateValue(v: T, r: ResultSet, idx: Int): Unit = r.updateBytes(idx, toBytes(v))

      override def hasLiteralForm: Boolean = false

      override def valueToSQLLiteral(v: T): String = throw new SlickException("no literal form") // if (v eq null) "NULL" else s"'${toLiteral(v)}'"
    }

    class CoordJdbcType(implicit override val classTag: ClassTag[Coord]) extends impl.DriverJdbcType[Coord] {

      override def sqlType: Int = java.sql.Types.OTHER

      override def sqlTypeName(sym: Option[FieldSymbol]): String = "geometry"

      override def getValue(r: ResultSet, idx: Int): Coord = {
        val value = r.getBytes(idx)
        if (r.wasNull) null.asInstanceOf[Coord] else fromBytes[Coord](value)
      }

      override def setValue(coord: Coord, p: PreparedStatement, idx: Int): Unit = {
        p.setString(idx, textFor(coord))
      }

      override def updateValue(v: Coord, r: ResultSet, idx: Int): Unit = r.updateString(idx, textFor(v))

      def textFor(coord: Coord) = {
        val text = s"POINT(${coord.lng} ${coord.lat})"
        val s = s"ST_PointFromText('$text')"
        println(s"Sending $s")
        s
      }

      override def hasLiteralForm: Boolean = false

      override def valueToSQLLiteral(v: Coord): String = throw new SlickException("no literal form") // if (v eq null) "NULL" else s"'${toLiteral(v)}'"
    }

    implicit val geometryTypeMapper = new GeometryJdbcType[Geometry]
    implicit val pointTypeMapper = new GeometryJdbcType[Point]
    implicit val coordMapper = new CoordJdbcType
    val distanceSphere = SimpleFunction.binary[Point, Point, Double]("ST_Distance_Sphere")

    class SpatialTable(tag: Tag) extends Table[SpatialRow](tag, "spatials") {
      def id = column[Int]("id", O.PrimaryKey)

      def lon = column[Double]("longitude")

      def lat = column[Double]("latitude")

      def p = column[Point]("geo")

      def * = (id, lon, lat, p) <> ((SpatialRow.apply _).tupled, SpatialRow.unapply)
    }

    class SpatialCoordsTable(tag: Tag) extends Table[CoordRow](tag, "spatial_coords") {
      def id = column[Int]("id", O.PrimaryKey)

      def coord = column[Coord]("coord")

      def * = (id, coord) <> ((CoordRow.apply _).tupled, CoordRow.unapply)
    }

  }

  def craftDb() = {
    val conf = DatabaseConf("jdbc:mysql://localhost:3306/gis?useSSL=false", "", "", DatabaseConf.MySQLDriver)
    val db = new SpatialSchema(BoatSchema.dataSource(conf), conf.profile)
    db.init()
    db
  }

  ignore("db") {
    val db = craftDb()
    import db._
    import db.api._
    val london = Coord(0.13, 51.5)
    val action = coordsTable += CoordRow(1, london)
    db.runAndAwait(action)
  }

  ignore("plain sql") {
    val db = craftDb()
    import db._
    import db.api._

    val london = Coord(0.13, 51.5)
    val sanfran = Coord(-122.4, 37.8)

    val writer = new WKBWriter()

    implicit object SetGeometry extends SetParameter[Geometry] {
      def apply(v: Geometry, pp: PositionedParameters) {
        pp.setBytes(writer.write(v))
      }
    }

    def insertCoord(rowId: Int, coord: Coord) = {
      val text = s"POINT(${coord.lng} ${coord.lat})"
      sqlu"""insert into spatials(id, longitude, latitude, geo) values($rowId, 1, 2, ST_PointFromText($text))"""
    }

    val action3 = insertCoord(15, london)
    val action4 = insertCoord(16, sanfran)
    await(db.run(action3))
    await(db.run(action4))
    runAndAwait(spatialTable.result) foreach println
    val q = spatialTable.filter(_.id === 7).map(_.p).join(spatialTable.filter(_.id === 8).map(_.p)).map {
      case (p1, p2) => distanceSphere(p1, p2)
    }
    println(runAndAwait(q.result))
  }
}
