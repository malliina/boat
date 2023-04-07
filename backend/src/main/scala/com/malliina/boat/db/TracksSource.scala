package com.malliina.boat.db

import cats.effect.IO
import com.malliina.boat.*
import com.malliina.boat.http.{BoatQuery, CarQuery, TrackQuery}
import com.malliina.boat.parsing.FullCoord
import com.malliina.values.{UserId, Username}

trait TrackInsertsDatabase[F[_]]:
  def updateTitle(track: TrackName, title: TrackTitle, user: UserId): F[JoinedTrack]
  def updateComments(track: TrackId, comments: String, user: UserId): F[JoinedTrack]
  def addBoat(boat: BoatName, user: UserId): F[BoatRow]
  def removeDevice(device: DeviceId, user: UserId): F[Int]
  def renameBoat(boat: DeviceId, newName: BoatName, user: UserId): F[BoatRow]

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
  def joinAsBoat(meta: DeviceMeta): F[TrackMeta]
  def joinAsDevice(meta: DeviceMeta): F[JoinedBoat]
  def saveSentences(sentences: SentencesEvent): F[Seq[KeyedSentence]]
  def saveCoords(coords: FullCoord): F[InsertedPoint]
  def saveCoordsFast(coords: FullCoord): F[TrackPointId]
  def saveLocations(locs: LocationUpdates, user: UserId): F[List[CarUpdateId]]

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
  def carHistory(user: MinimalUserInfo, filters: CarQuery): F[List[CarDrive]]
