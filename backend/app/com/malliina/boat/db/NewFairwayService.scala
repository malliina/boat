package com.malliina.boat.db

import com.malliina.boat.{Coord, CoordHash}

import scala.concurrent.Future

case class CoordFairway(coord: CoordHash, fairway: FairwayRow)
case class CoordFairways(coord: CoordHash, fairways: Seq[FairwayRow])

trait FairwaySource {
  def fairwaysAt(at: Coord): Future[Seq[FairwayRow]] = fairways(at.hash)
  def fairways(at: CoordHash): Future[Seq[FairwayRow]]
  def fairwaysAt(route: Seq[CoordHash]): Future[Seq[CoordFairways]]
}

object NewFairwayService {
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
