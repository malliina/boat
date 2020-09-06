package com.malliina.boat.db

import com.malliina.boat.parsing.GPSCoord
import com.malliina.boat.{GPSCoordsEvent, GPSInsertedPoint, GPSKeyedSentence, GPSSentencesEvent, GPSTimedCoord, MinimalUserInfo, TimeFormatter}

import scala.concurrent.Future

trait GPSSource {
  def history(user: MinimalUserInfo): Future[Seq[GPSCoordsEvent]]
  def saveSentences(sentences: GPSSentencesEvent): Future[Seq[GPSKeyedSentence]]
  def saveCoords(coord: GPSCoord): Future[GPSInsertedPoint]
}
