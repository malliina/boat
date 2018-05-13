package com.malliina.boat.db

import com.malliina.boat._
import com.malliina.boat.ws.Boat

import scala.concurrent.Future

trait TracksSource {
  def saveSentences(boat: Boat, sentences: Seq[RawSentence]): Future[Seq[SentenceKey]]

  def saveCoord(boat: Boat, coords: Seq[TrackPoint]): Future[Seq[TrackPointId]]

  def track(boat: Boat): Future[Track]

  def route(id: RouteId): Future[Route]
}
