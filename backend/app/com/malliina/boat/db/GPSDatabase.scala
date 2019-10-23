package com.malliina.boat.db

import com.malliina.boat.db.GPSDatabase.log
import com.malliina.boat.parsing.GPSCoord
import com.malliina.boat.{DeviceId, GPSCoordsEvent, GPSInsertedPoint, GPSKeyedSentence, GPSPointInput, GPSPointRow, GPSSentenceInput, GPSSentencePointLink, GPSSentencesEvent, GPSTimedCoord, JoinedBoat, MinimalUserInfo, TimeFormatter}
import com.malliina.measure.DistanceM
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object GPSDatabase {
  private val log = Logger(getClass)

  def apply(db: TracksSchema, ec: ExecutionContext): GPSDatabase = new GPSDatabase(db)(ec)

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

class GPSDatabase(val db: TracksSchema)(implicit ec: ExecutionContext) extends GPSSource {
  import db._
  import db.api._

  private val latestPoints =
    gpsPointsTable.groupBy(_.device).map { case (d, r) => (d, r.map(_.pointIndex).max) }

  def history(user: MinimalUserInfo): Future[Seq[GPSCoordsEvent]] = {
    val formatter = TimeFormatter(user.language)
    action {
      gpsPointsTable
        .join(latestPoints)
        .on((p, lp) => p.device === lp._1 && p.pointIndex === lp._2)
        .map(_._1)
        .join(boatsView.filter(_.username === user.username))
        .on(_.device === _.boat)
        .result
        .map { rows =>
          GPSDatabase.collectCoords(rows, formatter)
        }
    }
  }

  def saveSentences(sentences: GPSSentencesEvent): Future[Seq[GPSKeyedSentence]] = {
    val from = sentences.from
    val id = from.device
    val action = DBIO.sequence {
      sentences.sentences.map { raw =>
        (gpsSentenceInserts += GPSSentenceInput(raw, id)).map { key =>
          GPSKeyedSentence(key, raw, id)
        }
      }
    }
    insertLogged(action, id) { ids =>
      val suffix = if (ids.length == 1) "" else "s"
      s"${ids.length} sentence$suffix"
    }
  }

  def saveCoords(coord: GPSCoord): Future[GPSInsertedPoint] =
    insertLogged(saveCoordAction(coord), coord.device)(_ => "one point")

  private def saveCoordAction(coord: GPSCoord) = {
    val device = coord.device
    val action = for {
      previous <- gpsPointsTable
        .filter(_.device === device)
        .sortBy(_.pointIndex.desc)
        .take(1)
        .result
      pointIdx = previous.headOption.map(_.pointIndex).getOrElse(0) + 1
      diff <- previous.headOption
        .map(p => distanceCoords(p.coord, coord.coord.bind).result)
        .getOrElse(DBIO.successful(DistanceM.zero))
      pointId <- gpsPointInserts += GPSPointInput.forCoord(coord, pointIdx, diff)
      _ <- gpsSentencePointsTable ++= coord.parts.map(key => GPSSentencePointLink(key, pointId))
      d <- first(boatsView.filter(_.boat === device), s"Device not found: '$device'.")
    } yield GPSInsertedPoint(pointId, d)
    action.transactionally
  }

  private def insertLogged[R](action: DBIOAction[R, NoStream, Nothing], from: DeviceId)(
      describe: R => String
  ): Future[R] = {
    db.run(action)
      .map { keys =>
        log.debug(s"Inserted ${describe(keys)} from '$from'.")
        keys
      }
      .recoverWith {
        case t =>
          log.error(s"Error inserting data for '$from'.", t)
          Future.failed(t)
      }
  }
}
