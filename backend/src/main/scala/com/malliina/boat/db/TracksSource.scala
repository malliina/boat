package com.malliina.boat.db

import cats.effect.IO
import com.malliina.boat.*
import com.malliina.boat.http.{BoatQuery, TrackQuery}
import com.malliina.boat.parsing.FullCoord
import com.malliina.values.{UserId, Username}

trait TrackInsertsDatabase:
  def updateTitle(track: TrackName, title: TrackTitle, user: UserId): IO[JoinedTrack]
  def updateComments(track: TrackId, comments: String, user: UserId): IO[JoinedTrack]
  def addBoat(boat: BoatName, user: UserId): IO[BoatRow]
  def removeDevice(device: DeviceId, user: UserId): IO[Int]
  def renameBoat(boat: DeviceId, newName: BoatName, user: UserId): IO[BoatRow]

  /** If the given track and boat exist and are owned by the user, returns the track info.
    *
    * If the boat exists and is owned by the user but no track with the given name exists, the track
    * is created.
    *
    * If neither the track nor boat exist, they are created.
    *
    * If the track name or boat name is already taken by another user, the returned Future fails.
    *
    * @param meta
    *   track, boat and user info
    * @return
    *   track specs, or failure if there is a naming clash
    */
  def joinAsBoat(meta: DeviceMeta): IO[TrackMeta]
  def joinAsDevice(meta: DeviceMeta): IO[JoinedBoat]
  def saveSentences(sentences: SentencesEvent): IO[Seq[KeyedSentence]]
  def saveCoords(coords: FullCoord): IO[InsertedPoint]
  def saveCoordsFast(coords: FullCoord): IO[TrackPointId]

trait StatsSource:
  def stats(user: MinimalUserInfo, limits: TrackQuery, lang: Lang): IO[StatsResponse]

trait TracksSource:
  def tracksFor(user: MinimalUserInfo, filter: TrackQuery): IO[Tracks]
  def tracksBundle(user: MinimalUserInfo, filter: TrackQuery, lang: Lang): IO[TracksBundle]
  def ref(track: TrackName, language: Language): IO[TrackRef]
  def canonical(track: TrackCanonical, language: Language): IO[TrackRef]
  def track(track: TrackName, user: Username, query: TrackQuery): IO[TrackInfo]
  def full(track: TrackName, language: Language, query: TrackQuery): IO[FullTrack]
  def history(user: MinimalUserInfo, limits: BoatQuery): IO[Seq[CoordsEvent]]
