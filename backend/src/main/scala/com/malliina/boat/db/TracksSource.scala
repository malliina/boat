package com.malliina.boat.db

import com.malliina.boat.*
import com.malliina.boat.http.{BoatQuery, TrackQuery}
import com.malliina.boat.parsing.PointInsert
import com.malliina.values.{UserId, Username}

trait TrackInsertsDatabase[F[_]]:
  def updateTitle(track: TrackName, title: TrackTitle, user: UserId): F[JoinedTrack]
  def updateComments(track: TrackId, comments: String, user: UserId): F[JoinedTrack]
  def addSource(boat: BoatName, sourceType: SourceType, user: UserId): F[SourceRow]
  def removeDevice(device: DeviceId, user: UserId): F[Int]
  def renameBoat(boat: DeviceId, newName: BoatName, user: UserId): F[SourceRow]

  /** If the given track and source exist and are owned by the user, returns the track info.
    *
    * If the source exists and is owned by the user but no track with the given name exists, the
    * track is created.
    *
    * If neither the track nor source exist, they are created.
    *
    * If the track name or source name is already taken by another user, the returned effect fails.
    *
    * @param meta
    *   track, source and user info
    * @return
    *   track specs, or failure if there is a naming clash
    */
  def joinAsSource(meta: DeviceMeta): F[JoinResult]
  def saveSentences(sentences: SentencesEvent): F[Seq[KeyedSentence]]
  def saveCoords(coords: PointInsert): F[InsertedPoint]

trait StatsSource[F[_]]:
  def stats(user: MinimalUserInfo, limits: TrackQuery, lang: Lang): F[StatsResponse]

trait TracksSource[F[_]]:
  def tracksFor(user: MinimalUserInfo, filter: TrackQuery): F[Tracks]
  def tracksBundle(user: MinimalUserInfo, filter: TrackQuery, lang: Lang): F[TracksBundle]
  def ref(track: TrackName, language: Language): F[TrackRef]
  def canonical(track: TrackCanonical, language: Language): F[TrackRef]
  def track(track: TrackName, user: Username, query: TrackQuery): F[TrackInfo]
  def full(track: TrackName, language: Language, query: TrackQuery): F[FullTrack]
  def history(user: MinimalUserInfo, limits: BoatQuery): F[Seq[CoordsEvent]]
