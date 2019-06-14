package com.malliina.boat.db

import com.malliina.boat._
import com.malliina.boat.http.{BoatQuery, TrackQuery}
import com.malliina.boat.parsing.FullCoord
import com.malliina.values.{UserId, Username}

import scala.concurrent.Future

trait TracksSource {
  def modifyTitle(track: TrackName, title: TrackTitle, user: UserId): Future[JoinedTrack]

  def updateComments(track: TrackId, comments: String, user: UserId): Future[JoinedTrack]

  def addBoat(boat: BoatName, user: UserId): Future[BoatRow]

  def renameBoat(boat: BoatId, newName: BoatName, user: UserId): Future[BoatRow]

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

  def saveCoords(coords: FullCoord): Future[InsertedPoint]

  def tracksFor(user: MinimalUserInfo, filter: TrackQuery): Future[Tracks]

  def ref(track: TrackName, language: Language): Future[TrackRef]

  def canonical(track: TrackCanonical, language: Language): Future[TrackRef]

  def track(track: TrackName, user: Username, query: TrackQuery): Future[TrackInfo]

  def full(track: TrackName, language: Language, query: TrackQuery): Future[FullTrack]

  def history(user: MinimalUserInfo, limits: BoatQuery): Future[Seq[CoordsEvent]]
}
