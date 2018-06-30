package com.malliina.boat.db

import com.malliina.boat._
import com.malliina.boat.http.{BoatQuery, TrackQuery}
import com.malliina.boat.parsing.DatedCoord

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

  def saveCoords(coords: DatedCoord): Future[Seq[TrackPointId]]

  def renameBoat(old: BoatMeta, newName: BoatName): Future[BoatRow]

  def tracks(user: User, query: TrackQuery): Future[TrackSummaries]

  def history(user: User, limits: BoatQuery): Future[Seq[CoordsEvent]]
}
