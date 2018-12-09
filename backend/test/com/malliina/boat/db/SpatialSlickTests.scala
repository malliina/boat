package com.malliina.boat.db

import com.malliina.boat.{Coord, TrackId}
import com.malliina.concurrent.Execution.cached
import com.malliina.measure.{Speed, SpeedInt}
import tests.BaseSuite

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class SpatialSlickTests extends BaseSuite {
  val conf = DatabaseConf("jdbc:mysql://localhost:3306/boat?useSSL=false", "", "", DatabaseConf.MySQLDriver)
  val conf2 = DatabaseConf("jdbc:mysql://localhost:3306/gis?useSSL=false", "", "", DatabaseConf.MySQLDriver)
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

  ignore("migrate track indices") {
    val db = BoatSchema(conf)
    import db._
    import db.api._

    def migrate(track: TrackId) = {
      pointsTable.filter(_.track === track)
        .sortBy(p => (p.track, p.boatTime.asc, p.id.asc))
        .map(_.id)
        .result.flatMap { ts =>
        DBIO.sequence(
          ts.zipWithIndex.map { case (id, idx) =>
            pointsTable.filter(_.id === id).map(_.trackIndex).update(idx + 1)
          }
        )
      }
    }

    val action = for {
      ts <- pointsTable.map(_.track).distinct.result
      ok <- DBIO.sequence(ts.map(migrate))
    } yield ok.flatten.sum
    runAndAwait(action, 10000.seconds)
  }

  ignore("filter grouping") {
    val db = BoatSchema(conf)
    import db._
    import db.api._
    val minSpeed: Speed = 1.kmh
    val query = pointsTable
      .filter(_.boatSpeed >= minSpeed)
      .groupBy(_.track)
      .map { case (t, q) => (t, q.map(_.boatSpeed).avg) }
    query.result.statements.toList foreach println

    println(runAndAwait(query.result))
  }

  ignore("benchmark") {
    val db = BoatSchema(conf)
    import db._
    import db.api._
    tracksViewNonEmpty.result.statements.toList foreach println
    (1 to 30).foreach { _ =>
      await(timed(db.run(tracksViewNonEmpty.result)))
    }
  }

  def sequentially[T, R](ts: List[T])(code: T => Future[R]): Future[List[R]] =
    ts match {
      case head :: tail => code(head).flatMap { t => sequentially(tail)(code).map { ts => t :: ts } }
      case _ => Future.successful(Nil)
    }

  def timed[T](f: => Future[T]): Future[T] = {
    val start = System.currentTimeMillis()
    f.map { t =>
      val end = System.currentTimeMillis()
      println(s"${end - start} ms")
      t
    }
  }

  def craftDb() = {
    val db = new SpatialSchema(BoatSchema.dataSource(conf), conf.profileConf)
    db.init()
    db
  }
}
