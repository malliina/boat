package com.malliina.boat.db

import com.malliina.boat.{Coord, CoordHash}
import com.malliina.concurrent.Execution.cached

import scala.concurrent.Future

object FairwayService {
  def apply(db: FairwaySchema): FairwayService = new FairwayService(db)
}

class FairwayService(val db: FairwaySchema) {
  import db._
  import db.api._

  def fairwaysAt(at: Coord): Future[Seq[FairwayRow]] = fairways(at.hash)

  def fairways(at: CoordHash): Future[Seq[FairwayRow]] = action {
    fairwaysTable
      .join(fairwayCoordsTable.filter(c => c.hash === at))
      .on(_.id === _.fairway)
      .map { case (fairway, _) => fairway }
      .result
  }

  def fairwaysAt(route: Seq[CoordHash]): Future[Seq[CoordFairways]] = action {
    fairwaysTable
      .join(fairwayCoordsTable.filter(c => c.hash.inSet(route)))
      .on(_.id === _.fairway)
      .result
      .map { rows =>
        val map: Map[CoordHash, FairwayRow] = rows.map { case (f, c) => CoordFairway(c.hash, f) }
          .groupBy(_.coord)
          .map { case (k, v) => k -> v.head.fairway }
        route.flatMap { coord =>
          map.get(coord).map { fairway =>
            CoordFairways(coord, Seq(fairway))
          }
        }
      }
  }
}

case class CoordFairway(coord: CoordHash, fairway: FairwayRow)
case class CoordFairways(coord: CoordHash, fairways: Seq[FairwayRow])
