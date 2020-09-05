package com.malliina.boat.db

import com.malliina.boat.{Coord, CoordHash}
import io.getquill.SnakeCase

import scala.concurrent.Future

case class CoordFairway(coord: CoordHash, fairway: FairwayRow)
case class CoordFairways(coord: CoordHash, fairways: Seq[FairwayRow])

trait FairwaySource {
  def fairwaysAt(at: Coord): Future[Seq[FairwayRow]] = fairways(at.hash)
  def fairways(at: CoordHash): Future[Seq[FairwayRow]]
  def fairwaysAt(route: Seq[CoordHash]): Future[Seq[CoordFairways]]
}

object NewFairwayService {
  def apply(db: BoatDatabase[SnakeCase]): NewFairwayService = new NewFairwayService(db)

  def collect(rows: Seq[CoordFairway]): Seq[CoordFairways] =
    rows.foldLeft(Vector.empty[CoordFairways]) {
      case (acc, cf) =>
        val idx = acc.indexWhere(_.coord == cf.coord)
        if (idx >= 0) {
          val old = acc(idx)
          acc.updated(idx, old.copy(fairways = old.fairways :+ cf.fairway))
        } else {
          acc :+ CoordFairways(cf.coord, Seq(cf.fairway))
        }
    }
}

class NewFairwayService(val db: BoatDatabase[SnakeCase]) {
  import db._

  val fairwaysTable = quote(querySchema[FairwayRow]("fairways"))
  val fairwaysCoordsTable = quote(querySchema[FairwayCoord]("fairway_coords"))

  val fairwaysByCoords = quote { cs: Query[CoordHash] =>
    for {
      f <- fairwaysTable
      c <- fairwaysCoordsTable.filter(c => cs.contains(c.coordHash))
      if f.id == c.fairway
    } yield CoordFairway(c.coordHash, f)
  }

  def fairways(at: CoordHash): Future[Seq[FairwayRow]] = performAsync("Load fairways") {
    runIO(fairwaysByCoords(liftQuery(Seq(at))).map(_.fairway))
  }

  def fairwaysAt(route: Seq[CoordHash]): Future[Seq[CoordFairways]] =
    performAsync("Fairways at route") {
      runIO(fairwaysByCoords(liftQuery(route))).map(NewFairwayService.collect)
    }
}
