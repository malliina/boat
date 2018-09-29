package com.malliina.boat.db

import com.malliina.boat._
import com.malliina.boat.http.{BoatQuery, TrackQuery}
import com.malliina.boat.parsing.FullCoord
import com.malliina.values.{Email, UserId, Username}

import scala.concurrent.Future

trait TracksSource {
  def addBoat(boat: BoatName, user: UserId): Future[BoatRow]

  def renameBoat(boat: BoatId, user: UserId, newName: BoatName): Future[BoatRow]

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
  def join(meta: BoatTrackMeta): Future[TrackMeta]

  def saveSentences(sentences: SentencesEvent): Future[Seq[KeyedSentence]]

  def saveCoords(coords: FullCoord): Future[Seq[TrackRef]]

  def tracksFor(email: Email, filter: TrackQuery): Future[TrackSummaries]

  def distances(email: Email): Future[Seq[EasyDistance]]

  def tracks(user: Username, query: TrackQuery): Future[TrackSummaries]

  def summary(track: TrackName): Future[TrackSummary]

  def track(track: TrackName, user: Email, query: TrackQuery): Future[Seq[CombinedCoord]]

  def history(user: Username, limits: BoatQuery): Future[Seq[CoordsEvent]]
}
