package com.malliina.boat.db

import cats.data.NonEmptyList
import com.malliina.boat.db.DoobieTrackInserts.IO
import com.malliina.boat.{CoordHash, FairwayInfo}
import com.malliina.boat.http.BoatQuery
import doobie._
import doobie.implicits._
import cats.implicits._
import scala.concurrent.Future

object DoobieFairwayService {
  def apply(db: DoobieDatabase): DoobieFairwayService = new DoobieFairwayService(db)
}

class DoobieFairwayService(db: DoobieDatabase) extends FairwaySource {
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

  def insert(in: FairwayInfo): ConnectionIO[FairwayId] =
    sql"""insert into fairways(name_fi, name_se, start, end, depth, depth2, depth3, lighting, class_text, sea_area, state) 
         values (${in.nameFi}, ${in.nameSe}, ${in.start}, ${in.end}, ${in.depth}, ${in.depth2}, ${in.depth3}, ${in.lighting}, ${in.classText}, ${in.seaArea}, ${in.state})""".update
      .withUniqueGeneratedKeys[FairwayId]("id")

  def insertCoords(ins: List[FairwayCoordInput]): ConnectionIO[List[FairwayCoordId]] =
    ins.traverse { in =>
      sql"""insert into fairway_coords(coord, latitude, longitude, coord_hash, fairway)
         values(${in.coord}, ${in.latitude}, ${in.longitude}, ${in.coordHash}, ${in.fairway})""".update
        .withUniqueGeneratedKeys[FairwayCoordId]("id")
    }

  def delete = sql"truncate fairways".update.run
}
