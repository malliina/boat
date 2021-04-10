package com.malliina.boat.db

import cats.effect.IO
import com.malliina.boat.{Coord, CoordHash}

trait FairwaySource {
  def fairwaysAt(at: Coord): IO[Seq[FairwayRow]] = fairways(at.hash)
  def fairways(at: CoordHash): IO[Seq[FairwayRow]]
  def fairwaysAt(route: Seq[CoordHash]): IO[Seq[CoordFairways]]
}
