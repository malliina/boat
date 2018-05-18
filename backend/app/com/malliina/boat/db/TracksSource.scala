package com.malliina.boat.db

import com.malliina.boat._
import com.malliina.play.models.Username

import scala.concurrent.Future

case class TrackInfo(boat: BoatRow, track: TrackRow)

trait TracksSource {
  def newTrack(user: Username, boat: BoatName): Future[TrackInfo]

  def saveSentences(boat: BoatInfo, sentences: Seq[RawSentence]): Future[Seq[SentenceKey]]

  def saveCoord(boat: BoatInfo, coords: Seq[TrackPoint]): Future[Seq[TrackPointId]]

  def track(boat: BoatInfo): Future[Track]

  def route(id: RouteId): Future[Route]
}
