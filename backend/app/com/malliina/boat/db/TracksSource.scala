package com.malliina.boat.db

import com.malliina.boat._

import scala.concurrent.Future

trait TracksSource {
  /** If the given track and boat exist and are owned by the user, returns the track info.
    *
    * If the boat exists and is owned by the user but no track with the given name exists, the track is created.
    *
    * If neither the track nor boat exist, they are created.
    *
    * If the track name or boat name is already taken by another user, the returned Future fails.
    *
    * @param meta track, boat and user info
    * @return track specs, or failure if there is a naming clash
    */
  def join(meta: BoatMeta): Future[JoinedTrack]

  def saveSentences(sentences: SentencesEvent): Future[Seq[SentenceKey]]

  def saveCoords(coords: CoordsEvent): Future[Seq[TrackPointId]]

  def renameBoat(old: BoatMeta, newName: BoatName): Future[BoatRow]

  //  def saveCoord(boat: BoatInfo, coords: Seq[TrackPoint]): Future[Seq[TrackPointId]]

  //  def track(boat: BoatInfo): Future[Track]

  //  def route(id: RouteId): Future[Route]
}
