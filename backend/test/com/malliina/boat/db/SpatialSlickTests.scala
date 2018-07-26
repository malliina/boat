package com.malliina.boat.db

import com.malliina.boat.{Coord, TrackId}
import com.malliina.concurrent.ExecutionContexts.cached
import tests.BaseSuite

import scala.concurrent.duration.DurationInt

class SpatialSlickTests extends BaseSuite {
  val conf = DatabaseConf("jdbc:mysql://localhost:3306/gis?useSSL=false", "", "", DatabaseConf.MySQLDriver)
  //        val conf = DatabaseConf.inMemory

  ignore("insert coords directly") {
    val db = craftDb()
    import db._
    import db.api._

    val london = Coord(0.13, 51.5)
    val sanfran = Coord(-122.4, 37.8)
    runAndAwait(coordsTable.delete)
    val ids = runAndAwait(coordsTable.map(_.coord).returning(coordsTable.map(_.id)) ++= Seq(london, sanfran))
    val d = coordsTable.filter(_.id === ids.head).map(_.coord).join(coordsTable.filter(_.id === ids(1)).map(_.coord)).map {
      case (c1, c2) => distanceCoords(c1, c2)
    }
    println(runAndAwait(d.result))
  }

  ignore("migrate coords") {
    val db = BoatSchema(conf)
    import db._
    import db.api._
    val rowsChanged = for {
      rows <- pointsTable.result
      changed <- DBIO.sequence(rows.map(p => pointsTable.filter(_.id === p.id).map(_.coord).update(Coord(p.lon, p.lat))))
    } yield changed.sum
    await(db.run(rowsChanged), 600.seconds)
  }

  ignore("zip") {
    val db = craftDb()
    import db._
    import db.api._
    val london = Coord(0.13, 51.5)
    val sanfran = Coord(-122.4, 37.8)
    runAndAwait(coordsTable.delete)
    runAndAwait(coordsTable.map(_.coord) ++= Seq(london, sanfran))

    def distance(coords: Query[Rep[Coord], Coord, Seq]): Rep[Option[Double]] =
      coords.zipWithIndex.join(coords.zipWithIndex).on((c1, c2) => c1._2 === c2._2 - 1L)
        .map { case (l, r) => distanceCoords(l._1, r._1) }
        .sum

    val a = distance(coordsTable.sortBy(_.id).map(_.coord)).result
    runAndAwait(a) foreach println
  }

  ignore("distance") {
    val db = BoatSchema(conf)
    import db._
    import db.api._
    val d = distance(pointsTable.filter(_.track === TrackId(214)).map(_.coord)).result
    println(runAndAwait(d))
  }

  def craftDb() = {
    val db = new SpatialSchema(BoatSchema.dataSource(conf), conf.profileConf)
    db.init()
    db
  }
}
