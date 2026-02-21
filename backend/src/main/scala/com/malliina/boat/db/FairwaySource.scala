package com.malliina.boat.db

import com.malliina.geo.{Coord, CoordHash}

trait FairwaySource[F[_]] extends DoobieSQL:
  def fairwaysAt(at: Coord): F[Seq[FairwayRow]] = fairways(at.hash)
  def fairways(at: CoordHash): F[Seq[FairwayRow]]
  def fairwaysAt(route: Seq[CoordHash]): F[Seq[CoordFairways]]
