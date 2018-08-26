package com.malliina.boat.db

import java.time.Instant

import com.malliina.boat.{BoatInput, BoatName, BoatToken, Coord, TrackId, TrackInput, TrackName, TrackPointInput, TrackPointRow, UserToken}
import com.malliina.boat.Execution.cached
import com.malliina.measure.{Distance, Speed, SpeedInt, Temperature}
import com.malliina.values.Username
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

  ignore("ST_Distance_Sphere") {
    val db = BoatSchema(conf)
    import db._
    import db.api._
    val london = Coord(0.13, 51.5)
    val sanfran = Coord(-122.4, 37.8)

    def computeDistance(coords: Query[TrackPointsTable, TrackPointRow, Seq]): Rep[Option[Distance]] =
      coords
        .join(coords).on((c1, c2) => c1.track === c2.track && c1.id === c2.previous)
        .map { case (c1, c2) => distanceCoords(c1.coord, c2.coord) }
        .sum

    val action = for {
      user <- userInserts += NewUser(Username("test-run"), None, UserToken("test-token"), enabled = true)
      boat <- boatInserts += BoatInput(BoatName("test"), BoatToken("boat-token"), user)
      track <- trackInserts += TrackInput.empty(TrackName("test-track"), boat)
      sanfranId <- coordInserts += TrackPointInput(1, 2, sanfran, Speed.zero, Temperature.zeroCelsius, Distance.zero, Distance.zero, Instant.now, track, 1, None, Distance.zero)
      previous <- pointsTable.filter(_.track === track).sortBy(_.trackIndex.desc).take(1).result
      trackIdx = previous.headOption.map(_.trackIndex).getOrElse(0) + 1
      diff <- previous.headOption.map { p => distanceCoords(p.coord, london.bind).result }.getOrElse {
        DBIO.successful(Distance.zero)
      }
      londonId <- coordInserts += TrackPointInput(1, 2, london, Speed.zero, Temperature.zeroCelsius, Distance.zero, Distance.zero, Instant.now, track, trackIdx, Option(sanfranId), diff)
      distance <- computeDistance(pointsTable.filter(_.track === track)).result
      londonDiff <- pointsTable.filter(_.id === londonId).map(_.diff).result.headOption
      _ <- usersTable.filter(_.id === user).delete
    } yield (distance, londonDiff)
    val d = db.runAndAwait(action.transactionally)
    println(d)
  }

  ignore("migrate diffs") {
    val db = BoatSchema(conf)
    import db._
    import db.api._
    val coords = pointsTable
    val diffs = coords
      .join(coords).on((c1, c2) => c1.track === c2.track && c1.id === c2.previous)
      .map { case (c1, c2) => (c2.id, distanceCoords(c1.coord, c2.coord)) }
    val action = diffs.result.flatMap { rows =>
      val updates = rows.map { case (id, diff) => pointsTable.filter(_.id === id).map(_.diff).update(diff) }
      DBIO.sequence(updates)
    }
    db.runAndAwait(action, 36000.seconds)
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

  def sequentially[T, R](ts: List[T])(code: T => Future[R]): Future[List[R]] = {
    ts match {
      case head :: tail => code(head).flatMap { t => sequentially(tail)(code).map { ts => t :: ts } }
      case _ => Future.successful(Nil)
    }
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
