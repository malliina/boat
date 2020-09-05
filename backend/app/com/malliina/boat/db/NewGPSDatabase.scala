package com.malliina.boat.db

import com.malliina.boat.parsing.GPSCoord
import com.malliina.boat.{GPSCoordsEvent, GPSInsertedPoint, GPSKeyedSentence, GPSSentencesEvent, GPSTimedCoord, MinimalUserInfo, TimeFormatter}

import scala.concurrent.Future

trait GPSSource {
  def history(user: MinimalUserInfo): Future[Seq[GPSCoordsEvent]]
  def saveSentences(sentences: GPSSentencesEvent): Future[Seq[GPSKeyedSentence]]
  def saveCoords(coord: GPSCoord): Future[GPSInsertedPoint]
}

object NewGPSDatabase {
  def collect(rows: Seq[JoinedGPS], formatter: TimeFormatter) =
    rows.foldLeft(Vector.empty[GPSCoordsEvent]) {
      case (acc, joined) =>
        val device = joined.device
        val point = joined.point
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
