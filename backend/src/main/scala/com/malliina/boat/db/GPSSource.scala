package com.malliina.boat.db

import cats.effect.IO
import com.malliina.boat.parsing.GPSCoord
import com.malliina.boat.{GPSCoordsEvent, GPSInsertedPoint, GPSKeyedSentence, GPSSentencesEvent, MinimalUserInfo}

trait GPSSource:
  def history(user: MinimalUserInfo): IO[Seq[GPSCoordsEvent]]
  def saveSentences(sentences: GPSSentencesEvent): IO[Seq[GPSKeyedSentence]]
  def saveCoords(coord: GPSCoord): IO[GPSInsertedPoint]
