package com.malliina.boat.db

import java.time.Instant
import com.malliina.boat.{Boat, BoatName, BoatPrimitives, BoatRef, BoatToken, CombinedCoord, DateVal, DeviceId, FormattedDateTime, FriendInvite, FriendRef, GPSPointRow, InviteState, JoinedBoat, JoinedTrack, Language, MonthVal, TimeFormatter, TimedCoord, TrackCanonical, TrackId, TrackName, TrackPointId, TrackPointRow, TrackTitle, UserToken, YearVal}
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.{Email, UserId, Username}
import io.circe.*
import io.circe.generic.semiauto.*
import scala.concurrent.duration.FiniteDuration

case class BoatRow(id: DeviceId, name: BoatName, token: BoatToken, owner: UserId, added: Instant):
  def toBoat = Boat(id, name, token, added.toEpochMilli)

case class UserRow(
  id: UserId,
  user: Username,
  email: Option[Email],
  token: UserToken,
  language: Language,
  enabled: Boolean,
  added: Instant
)

case class UserBoatRow(user: UserId, boat: DeviceId, state: InviteState, added: Instant)

case class TrackRow(
  id: TrackId,
  name: TrackName,
  boat: DeviceId,
  avgSpeed: Option[SpeedM],
  avgWaterTemp: Option[Temperature],
  points: Int,
  distance: DistanceM,
  title: Option[TrackTitle],
  canonical: TrackCanonical,
  comments: Option[String],
  added: Instant
)

case class TrackOut(
  id: TrackId,
  name: TrackName,
  title: Option[TrackTitle],
  top: Option[SpeedM],
  min: Option[FormattedDateTime],
  max: Option[FormattedDateTime],
  coord: TimedCoord
)

object TrackOut:
  implicit val json: Codec[TrackOut] = deriveCodec[TrackOut]

case class Partial(
  device: DeviceId,
  id: TrackId,
  start: Instant,
  end: Instant,
  coord: CombinedCoord
):
  def strip =
    StrippedPartial(
      device,
      id,
      Option(start),
      Option(end),
      coord.timed(TimeFormatter.se),
      None
    )

case class StrippedPartial(
  device: DeviceId,
  id: TrackId,
  start: Option[Instant],
  end: Option[Instant],
  coord: TimedCoord,
  date: Option[DateVal]
)

object StrippedPartial:
  implicit val json: Codec[StrippedPartial] = deriveCodec[StrippedPartial]

case class TrackTimes(
  track: TrackId,
  start: Instant,
  end: Instant,
  duration: FiniteDuration,
  date: DateVal,
  month: MonthVal,
  year: YearVal
)

object TrackTimes:
  implicit val dur: Codec[FiniteDuration] = BoatPrimitives.durationFormat
  implicit val json: Codec[TrackTimes] = deriveCodec[TrackTimes]

case class DailyAggregates(
  date: DateVal,
  distance: Option[DistanceM],
  duration: Option[FiniteDuration],
  tracks: Long,
  days: Long
)

object DailyAggregates:
  implicit val dur: Codec[FiniteDuration] = BoatPrimitives.durationFormat
  implicit val json: Codec[DailyAggregates] = deriveCodec[DailyAggregates]

case class MonthlyAggregates(
  year: YearVal,
  month: MonthVal,
  distance: Option[DistanceM],
  duration: Option[FiniteDuration],
  tracks: Long,
  days: Long
)
case class YearlyAggregates(
  year: YearVal,
  distance: Option[DistanceM],
  duration: Option[FiniteDuration],
  tracks: Long,
  days: Long
)
case class AllTimeAggregates(
  from: Option[DateVal],
  to: Option[DateVal],
  distance: Option[DistanceM],
  duration: Option[FiniteDuration],
  tracks: Long,
  days: Long
)
object AllTimeAggregates:
  val empty = AllTimeAggregates(None, None, None, None, 0L, 0L)

case class TrackCoord(track: JoinedTrack, row: TrackPointRow)
case class TopTrack(track: TrackRow, times: TrackTimes, coord: CombinedCoord)
case class TrackTime(track: TrackRow, times: TrackTimes)
case class TrackTop(track: TrackId, top: Option[TrackPointId])

case class InviteRow(boat: BoatRef, state: InviteState, added: Instant)
case class FriendRow(boat: BoatRef, friend: FriendRef, state: InviteState, added: Instant)

case class JoinedUser(
  user: UserRow,
  boat: Option[BoatRow],
  invite: Option[InviteRow],
  friend: Option[FriendRow]
)

case class JoinedGPS(point: GPSPointRow, device: JoinedBoat)
