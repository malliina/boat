package com.malliina.boat.db

import cats.effect.IO
import com.malliina.boat.parsing.GPSCoord
import com.malliina.boat.{GPSCoordsEvent, GPSInsertedPoint, GPSKeyedSentence, GPSSentencesEvent, MinimalUserInfo}

trait GPSSource[F[_]]:
  def history(user: MinimalUserInfo): F[Seq[GPSCoordsEvent]]
  def saveSentences(sentences: GPSSentencesEvent): F[List[GPSKeyedSentence]]
  def saveCoords(coord: GPSCoord): F[GPSInsertedPoint]
