package com.malliina.boat.db

import cats.data.NonEmptyList
import com.malliina.boat.CoordHash
import com.malliina.boat.http.BoatQuery
import doobie._
import doobie.implicits._

import scala.concurrent.Future

class DoobieFairwayService(db: DoobieDatabase) {
  import DoobieMappings._
  def byCoords(coords: NonEmptyList[CoordHash]) = {
    val inClause = Fragments.in(fr"fc.coord_hash", coords)
    sql"""select fc.coord_hash, f.id, f.name_fi, f.name_se, f.start, f.end, f.depth, f.depth2, f.depth3, f.lighting, f.class_text, f.sea_area, f.state
          from fairways f, fairway_coords fc 
          where f.id = fc.fairway and $inClause"""
      .query[CoordFairway]
      .to[List]
  }

  def fairways(at: CoordHash): Future[Seq[FairwayRow]] = db.run {
    byCoords(NonEmptyList(at, Nil)).map(_.map(_.fairway))
  }

  def fairwaysAt(route: Seq[CoordHash]): Future[Seq[CoordFairways]] = db.run {
    BoatQuery
      .toNonEmpty(route.toList)
      .map { routes =>
        byCoords(routes).map { cs => NewFairwayService.collect(cs) }
      }
      .getOrElse {
        AsyncConnectionIO.pure(Nil)
      }
  }
}
