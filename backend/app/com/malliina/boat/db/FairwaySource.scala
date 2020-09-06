package com.malliina.boat.db

import com.malliina.boat.{Coord, CoordHash}

import scala.concurrent.Future

trait FairwaySource {
  def fairwaysAt(at: Coord): Future[Seq[FairwayRow]] = fairways(at.hash)
  def fairways(at: CoordHash): Future[Seq[FairwayRow]]
  def fairwaysAt(route: Seq[CoordHash]): Future[Seq[CoordFairways]]
}
