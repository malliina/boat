package com.malliina.boat.db

import com.malliina.boat._

import scala.concurrent.Future

case class TrackInfo(boat: BoatRow, track: TrackRow)

trait TracksSource {
  //  def newTrack(user: Username, boat: BoatName): Future[TrackInfo]

  def saveSentences(sentences: SentencesEvent): Future[Seq[SentenceKey]]

  def registerBoat(meta: BoatMeta): Future[BoatId]

  def renameBoat(old: BoatMeta, newName: BoatName): Future[BoatRow]

  //  def saveCoord(boat: BoatInfo, coords: Seq[TrackPoint]): Future[Seq[TrackPointId]]

  //  def track(boat: BoatInfo): Future[Track]

  //  def route(id: RouteId): Future[Route]
}
