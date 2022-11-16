package com.malliina.boat.db

import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits.*
import com.malliina.boat.db.DoobieFairwayService.collect
import com.malliina.boat.{CoordHash, FairwayInfo}
import doobie.*
import doobie.implicits.*

object DoobieFairwayService:
  def collect(rows: Seq[CoordFairway]): Seq[CoordFairways] =
    rows.foldLeft(Vector.empty[CoordFairways]) { case (acc, cf) =>
      val idx = acc.indexWhere(_.coord == cf.coord)
      if idx >= 0 then
        val old = acc(idx)
        acc.updated(idx, old.copy(fairways = old.fairways :+ cf.fairway))
      else acc :+ CoordFairways(cf.coord, Seq(cf.fairway))
    }

class DoobieFairwayService(db: DoobieDatabase) extends FairwaySource:
  import DoobieMappings.*
  def byCoords(coords: NonEmptyList[CoordHash]) =
    val inClause = Fragments.in(fr"fc.coord_hash", coords)
    sql"""select fc.coord_hash, f.id, f.name_fi, f.name_se, f.start, f.end, f.depth, f.depth2, f.depth3, f.lighting, f.class_text, f.sea_area, f.state
          from fairways f, fairway_coords fc 
          where f.id = fc.fairway and $inClause"""
      .query[CoordFairway]
      .to[List]

  def fairways(at: CoordHash): IO[Seq[FairwayRow]] = db.run {
    byCoords(NonEmptyList(at, Nil)).map(_.map(_.fairway))
  }

  def fairwaysAt(route: Seq[CoordHash]): IO[Seq[CoordFairways]] = db.run {
    route.toList.toNel.map { routes =>
      byCoords(routes).map { cs => collect(cs) }
    }.getOrElse {
      pure(Nil)
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
