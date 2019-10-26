package com.malliina.boat.db

import com.malliina.boat.parsing.GPSCoord
import com.malliina.boat.{GPSCoordsEvent, GPSInsertedPoint, GPSKeyedSentence, GPSPointId, GPSPointRow, GPSSentencePointLink, GPSSentenceRow, GPSSentencesEvent, GPSTimedCoord, JoinedBoat, MinimalUserInfo, TimeFormatter}
import com.malliina.measure.DistanceM
import com.malliina.values.Username
import io.getquill.SnakeCase

import scala.concurrent.Future

trait GPSSource {
  def history(user: MinimalUserInfo): Future[Seq[GPSCoordsEvent]]
  def saveSentences(sentences: GPSSentencesEvent): Future[Seq[GPSKeyedSentence]]
  def saveCoords(coord: GPSCoord): Future[GPSInsertedPoint]
}

object NewGPSDatabase {
  def apply(db: BoatDatabase[SnakeCase]): NewGPSDatabase =
    new NewGPSDatabase(db)

  def collect(cs: Seq[JoinedGPS], formatter: TimeFormatter): Seq[GPSCoordsEvent] =
    collectCoords(cs.map(c => (c.point, c.device)), formatter)

  def collectCoords(rows: Seq[(GPSPointRow, JoinedBoat)], formatter: TimeFormatter) =
    rows.foldLeft(Vector.empty[GPSCoordsEvent]) {
      case (acc, (point, device)) =>
        val idx = acc.indexWhere(_.from.device == device.device)
        val coord = GPSTimedCoord(point.id, point.coord, formatter.timing(point.gpsTime))
        if (idx >= 0) {
          val old = acc(idx)
          acc.updated(idx, old.copy(coords = coord :: old.coords))
        } else {
          acc :+ GPSCoordsEvent(List(coord), device.strip)
        }
    }
}

class NewGPSDatabase(val db: BoatDatabase[SnakeCase]) extends GPSSource {
  import db._

  val gpsPointsTable = quote(querySchema[GPSPointRow]("gps_points"))
  val gpsSentencePointsTable = quote(querySchema[GPSSentencePointLink]("gps_sentence_points"))
  val gpsSentencesTable = quote(querySchema[GPSSentenceRow]("gps_sentences"))

  val latestPoints = quote {
    gpsPointsTable.groupBy(_.device).map { case (d, r) => (d, r.map(_.pointIndex).max) }
  }
  val historyByUser = quote { user: Username =>
    for {
      boat <- boatsView.filter(_.username == user)
      point <- gpsPointsTable
      if point.device == boat.device
      (device, latest) <- latestPoints
      if boat.device == device && latest.contains(point.pointIndex)
    } yield JoinedGPS(point, boat)
  }

  def history(user: MinimalUserInfo): Future[Seq[GPSCoordsEvent]] = performAsync("GPS history") {
    runIO(historyByUser(lift(user.username))).map { cs =>
      NewGPSDatabase.collect(cs, TimeFormatter(user.language))
    }
  }

  def saveSentences(sentences: GPSSentencesEvent): Future[Seq[GPSKeyedSentence]] =
    transactionally("Save GPS sentences") {
      val from = sentences.from
      IO.traverse(sentences.sentences) { s =>
        runIO(
          gpsSentencesTable
            .insert(_.sentence -> lift(s), _.device -> lift(from.device))
            .returningGenerated(_.id)
        ).map { id =>
          GPSKeyedSentence(id, s, from.device)
        }
      }
    }

  def saveCoords(coord: GPSCoord): Future[GPSInsertedPoint] =
    transactionally("Save GPS coords") {
      val device = coord.device
      val previous = quote {
        gpsPointsTable.filter(_.device == lift(device)).sortBy(_.pointIndex)(Ord.desc).take(1)
      }
      for {
        prev <- runIO(previous).map(_.headOption)
        diff <- prev.map { p =>
          runIO(selectDistance(lift(p.coord), lift(coord.coord)))
            .map(_.headOption.getOrElse(DistanceM.zero))
        }.getOrElse { IO.successful(DistanceM.zero) }
        pointId: GPSPointId <- runIO {
          gpsPointsTable
            .insert(
              _.longitude -> lift(coord.lng),
              _.latitude -> lift(coord.lat),
              _.satellites -> lift(coord.satellites),
              _.fix -> lift(coord.fix),
              _.gpsTime -> lift(coord.gpsTime),
              _.diff -> lift(diff),
              _.device -> lift(device),
              _.pointIndex -> lift(prev.map(_.pointIndex).getOrElse(0) + 1)
            )
            .returningGenerated(_.id)
        }
        ids <- IO.traverse(coord.parts) { p =>
          runIO(gpsSentencePointsTable.insert(_.sentence -> lift(p), _.point -> lift(pointId)))
        }
        d <- runIO(boatsView.filter(_.device == lift(device)))
          .map(_.headOption.getOrElse(fail(s"Device not found: '$device'.")))
      } yield GPSInsertedPoint(pointId, d)
    }
}
