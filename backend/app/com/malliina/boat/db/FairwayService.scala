package com.malliina.boat.db

import com.malliina.boat.{Coord, CoordHash}

import scala.concurrent.Future

object FairwayService {
  def apply(db: BoatSchema) = new FairwayService(db)
}

class FairwayService(val db: BoatSchema) extends DatabaseOps(db) {
  import db.{fairwaysTable, fairwayCoordsTable}
  import db.api._

  def fairwaysAt(at: Coord) = fairways(at.hash)

  def fairways(at: CoordHash): Future[Seq[FairwayRow]] = action {
    fairwaysTable
      .join(fairwayCoordsTable.filter(c => c.hash === at))
      .on(_.id === _.fairway)
      .map(_._1)
      .result
  }
}
