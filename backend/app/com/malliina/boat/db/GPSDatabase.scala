package com.malliina.boat.db

import com.malliina.boat.db.GPSDatabase.log
import com.malliina.boat.parsing.GPSCoord
import com.malliina.boat.{DeviceId, GPSKeyedSentence, GPSPointInput, GPSSentenceInput, GPSSentencePointLink, GPSSentencesEvent}
import com.malliina.measure.DistanceM
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object GPSDatabase {
  private val log = Logger(getClass)

  def apply(db: TracksSchema, ec: ExecutionContext): GPSDatabase = new GPSDatabase(db)(ec)
}

class GPSDatabase(val db: TracksSchema)(implicit ec: ExecutionContext) {
  import db._
  import db.api._

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

  def saveCoords(coord: GPSCoord) = insertLogged(saveCoordAction(coord), coord.device)(_ => "one point")

  def saveCoordAction(coord: GPSCoord) = {
    val device = coord.device
    val action = for {
      previous <- gpsPointsTable.filter(_.device === device).sortBy(_.pointIndex.desc).take(1).result
      pointIdx = previous.headOption.map(_.pointIndex).getOrElse(0) + 1
      diff <- previous.headOption
        .map(p => distanceCoords(p.coord, coord.coord.bind).result)
        .getOrElse(DBIO.successful(DistanceM.zero))
      pointId <- gpsPointInserts += GPSPointInput.forCoord(coord, pointIdx, diff)
      _ <- gpsSentencePointsTable ++= coord.parts.map(key => GPSSentencePointLink(key, pointId))
      point <- first(gpsPointsTable.filter(_.id === pointId), s"Point not found: '$pointId'.")
    } yield point
    action.transactionally
  }

  private def insertLogged[R](action: DBIOAction[R, NoStream, Nothing], from: DeviceId)(
      describe: R => String): Future[R] = {
    db.run(action)
      .map { keys =>
        log.info(s"Inserted ${describe(keys)} from '$from'.")
        keys
      }
      .recoverWith {
        case t =>
          log.error(s"Error inserting data for '$from'.", t)
          Future.failed(t)
      }
  }
}
