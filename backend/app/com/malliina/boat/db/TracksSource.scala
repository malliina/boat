package com.malliina.boat.db

import com.malliina.boat._
import com.malliina.boat.ws.Boat
import com.malliina.play.models.Username

import scala.concurrent.Future

trait TrackMeta {
  def user: Username

  def boat: BoatName

  def track: TrackName
}

case class TrackInfo(boat: BoatRow, track: TrackRow)

trait TracksSource {
  def newTrack(user: Username, boat: BoatName): Future[TrackInfo]

  def saveSentences(boat: Boat, sentences: Seq[RawSentence]): Future[Seq[SentenceKey]]

  def saveCoord(boat: Boat, coords: Seq[TrackPoint]): Future[Seq[TrackPointId]]

  def track(boat: Boat): Future[Track]

  def route(id: RouteId): Future[Route]
}
