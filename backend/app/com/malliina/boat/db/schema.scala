package com.malliina.boat.db

import java.time.Instant

import com.malliina.boat.{
  Boat,
  BoatName,
  BoatToken,
  DeviceId,
  Language,
  TrackCanonical,
  TrackId,
  TrackName,
  TrackTitle,
  UserToken
}
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.{Email, UserId, Username}

// Schema used by Quill. Member names match database columns.

case class BoatRow(id: DeviceId,
                   name: BoatName,
                   token: BoatToken,
                   owner: UserId,
                   added: Instant) {
  def toBoat = Boat(id, name, token, added.toEpochMilli)
}

case class UserRow(id: UserId,
                   user: Username,
                   email: Option[Email],
                   token: UserToken,
                   language: Language,
                   enabled: Boolean,
                   added: Instant)

case class TrackRow(id: TrackId,
                    name: TrackName,
                    boat: DeviceId,
                    avgSpeed: Option[SpeedM],
                    avgWaterTemp: Option[Temperature],
                    points: Int,
                    distance: DistanceM,
                    title: Option[TrackTitle],
                    canonical: TrackCanonical,
                    comments: Option[String],
                    added: Instant)
