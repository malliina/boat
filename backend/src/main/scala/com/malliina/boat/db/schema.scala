package com.malliina.boat.db

import com.comcast.ip4s.{Host, Port}
import com.malliina.boat.*
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.{Email, UserId, Username}
import doobie.implicits.toSqlInterpolator
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

case class SourceRow(
  id: DeviceId,
  name: BoatName,
  sourceType: SourceType,
  token: BoatToken,
  ip: Option[Host],
  port: Option[Port],
  owner: UserId,
  added: Instant
):
  def toBoat = Boat(id, name, sourceType, token, gps, added.toEpochMilli)
  private def gps =
    for
      gpsIp <- ip
      gpsPort <- port
    yield GPSInfo(gpsIp, gpsPort)

object SourceRow:
  val columns = fr0"b.id, b.name, b.source_type, b.token, b.gps_ip, b.gps_port, b.owner, b.added"

case class UserRow(
  id: UserId,
  user: Username,
  email: Option[Email],
  token: UserToken,
  language: Language,
  enabled: Boolean,
  added: Instant
)

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
  given dur: Codec[FiniteDuration] = BoatPrimitives.durationFormat
  given json: Codec[TrackTimes] = deriveCodec[TrackTimes]

case class DailyAggregates(
  date: DateVal,
  distance: Option[DistanceM],
  duration: Option[FiniteDuration],
  tracks: Long,
  days: Long
)

object DailyAggregates:
  given Codec[FiniteDuration] = BoatPrimitives.durationFormat
  given Codec[DailyAggregates] = deriveCodec[DailyAggregates]

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
case class InviteRow(boat: BoatRef, state: InviteState, added: Instant)
case class FriendRow(boat: BoatRef, friend: FriendRef, state: InviteState, added: Instant)

case class JoinedUser(
  user: UserRow,
  boat: Option[SourceRow],
  invite: Option[InviteRow],
  friend: Option[FriendRow],
  hasCars: Boolean
)

case class VesselRow(
  id: VesselRowId,
  mmsi: Mmsi,
  name: VesselName,
  coord: Coord,
  sog: SpeedM,
  cog: Double,
  draft: DistanceM,
  destination: Option[String],
  heading: Option[Int],
  eta: Long,
  added: Instant
) derives Codec.AsObject

case class VesselUpdate(
  coord: Coord,
  sog: SpeedM,
  cog: Double,
  destination: Option[String],
  heading: Option[Int],
  eta: Long,
  added: Instant
) derives Codec.AsObject

object VesselUpdate:
  def from(row: VesselRow): VesselUpdate =
    VesselUpdate(row.coord, row.sog, row.cog, row.destination, row.heading, row.eta, row.added)

case class VesselHistory(mmsi: Mmsi, name: VesselName, draft: DistanceM, updates: Seq[VesselUpdate])
  derives Codec.AsObject

case class VesselHistoryResponse(vessels: Seq[VesselHistory]) derives Codec.AsObject

case class VesselResult(mmsi: Mmsi, name: VesselName, draft: DistanceM, added: Instant)
  derives Codec.AsObject

case class VesselsResponse(vessels: List[VesselResult]) derives Codec.AsObject

object Values:
  opaque type RowsChanged = Int

  object RowsChanged:
    def apply(i: Int): RowsChanged = i
  extension (rc: RowsChanged) def +(other: RowsChanged): RowsChanged = RowsChanged(rc + other)

  opaque type VesselUpdateId = Long
  object VesselUpdateId:
    def apply(id: Long): VesselUpdateId = id
  extension (u: VesselUpdateId) def raw: Long = u
