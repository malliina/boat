package com.malliina.boat.db

import com.malliina.boat.*

import doobie.*
import doobie.implicits.*
import cats.effect.IO
import cats.implicits.*
import com.malliina.boat.parsing.GPSCoord
import com.malliina.measure.DistanceM

object DoobieGPSDatabase:
  def apply(db: DoobieDatabase): DoobieGPSDatabase = new DoobieGPSDatabase(db)

  def collect(rows: Seq[JoinedGPS], formatter: TimeFormatter) =
    rows.foldLeft(Vector.empty[GPSCoordsEvent]) { case (acc, joined) =>
      val device = joined.device
      val point = joined.point
      val idx = acc.indexWhere(_.from.device == device.device)
      val coord = GPSTimedCoord(point.id, point.coord, formatter.timing(point.gpsTime))
      if idx >= 0 then
        val old = acc(idx)
        acc.updated(idx, old.copy(coords = coord :: old.coords))
      else acc :+ GPSCoordsEvent(List(coord), device.strip)
    }

class DoobieGPSDatabase(db: DoobieDatabase) extends GPSSource with DoobieSQL:
  import DoobieMappings.*

  val pointColumns =
    fr0"p.id, p.longitude, p.latitude, p.coord, p.satellites, p.fix, p.point_index, p.gps_time, p.diff, p.device, p.added"

  def history(user: MinimalUserInfo): IO[Seq[GPSCoordsEvent]] = db.run {
    sql"""select $pointColumns, b.id, b.name, b.token, b.uid, b.user, b.email, b.language
          from (${CommonSql.boats} and u.user = ${user.username}) b, gps_points p, (select device, max(point_index) pointIndex from gps_points group by device) l
          where p.device = b.id and p.device = l.device and p.point_index = l.pointIndex"""
      .query[JoinedGPS]
      .to[List]
      .map { joined =>
        DoobieGPSDatabase.collect(joined, TimeFormatter(user.language))
      }
  }

  def saveSentences(sentences: GPSSentencesEvent): IO[Seq[GPSKeyedSentence]] = db.run {
    val from = sentences.from
    sentences.sentences.toList.traverse { s =>
      sql"insert into gps_sentences(sentence, device) values ($s, ${from.device})".update
        .withUniqueGeneratedKeys[GPSSentenceKey]("id")
        .map { id =>
          GPSKeyedSentence(id, s, from.device)
        }
    }
  }

  def saveCoords(coord: GPSCoord): IO[GPSInsertedPoint] = db.run {
    val device = coord.device
    val previous =
      sql"select $pointColumns from gps_points where device = $device order by point_index desc limit 1"
        .query[GPSPointRow]
        .option
    for
      prev <- previous
      diff <- prev.map(p => computeDistance(p.coord, coord.coord)).getOrElse(pure(DistanceM.zero))
      pointId <- insertPoint(coord, diff, prev.map(_.pointIndex).getOrElse(0) + 1)
      ids <- coord.parts.toList.traverse { p =>
        sql"insert into gps_sentence_points(sentence, point) values($p, $pointId)".update.run
      }
      d <- CommonSql.boatsById(device)
    yield GPSInsertedPoint(pointId, d)
  }

  private def insertPoint(c: GPSCoord, diff: DistanceM, idx: Int): ConnectionIO[GPSPointId] =
    sql"""insert into gps_points(longitude, latitude, satellites, fix, gps_time, diff, device, point_index)
          values(${c.lng}, ${c.lat}, ${c.satellites}, ${c.fix}, ${c.gpsTime}, $diff, ${c.device}, $idx)""".update
      .withUniqueGeneratedKeys[GPSPointId]("id")
