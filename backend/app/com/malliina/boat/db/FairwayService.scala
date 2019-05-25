package com.malliina.boat.db

import com.malliina.boat.{Coord, CoordHash}
import com.malliina.concurrent.Execution.cached

import scala.concurrent.Future

object FairwayService {
  def apply(db: BoatSchema) = new FairwayService(db)
}

class FairwayService(val db: BoatSchema) extends DatabaseOps(db) {
  import db.api._
  import db.{fairwayCoordsTable, fairwaysTable}

  def fairwaysAt(at: Coord) = fairways(at.hash)

  def fairways(at: CoordHash): Future[Seq[FairwayRow]] = action {
    fairwaysTable
      .join(fairwayCoordsTable.filter(c => c.hash === at))
      .on(_.id === _.fairway)
      .map { case (fairway, _) => fairway }
      .result
  }

  def fairwaysAt(route: Seq[CoordHash]): Future[Seq[CoordFairway]] = action {
    fairwaysTable
      .join(fairwayCoordsTable.filter(c => c.hash.inSet(route)))
      .on(_.id === _.fairway)
      .result
      .map { rows =>
        val map = rows.map { case (f, c) => CoordFairway(c.hash, f) }
          .groupBy(_.coord)
          .mapValues(_.head.fairway)
        route.flatMap { coord =>
          map.get(coord).map { fairway =>
            CoordFairway(coord, fairway)
          }
        }
      }
  }
}

case class CoordFairway(coord: CoordHash, fairway: FairwayRow)
