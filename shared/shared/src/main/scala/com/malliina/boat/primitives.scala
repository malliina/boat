package com.malliina.boat

import cats.Show
import cats.implicits.toShow
import com.malliina.values.{JsonCompanion, Readable}
import io.circe.{Codec, Decoder, Encoder}
import org.typelevel.ci.CIString

abstract class ShowableString[T] extends JsonCompanion[String, T]:
  given Show[T] = Show(t => write(t))

abstract class ShowableLong[T] extends JsonCompanion[Long, T]:
  given Show[T] = Show(t => write(t).toString)

given Codec[CIString] = Codec.from(
  Decoder.decodeString.map(s => CIString(s)),
  Encoder.encodeString.contramap[CIString](_.toString)
)
given Readable[CIString] = Readable.string.map(s => CIString(s))

opaque type BoatName = CIString
object BoatName extends JsonCompanion[CIString, BoatName]:
  val Key = "boatName"
  override def apply(raw: CIString): BoatName = raw
  override def write(t: BoatName): CIString = t
  given Show[BoatName] = Show.show(bn => write(bn).toString)

opaque type TrackName = String
object TrackName extends ShowableString[TrackName]:
  val Key = "track"
  override def apply(raw: String): TrackName = raw
  override def write(t: TrackName): String = t

opaque type TrackTitle = String
object TrackTitle extends ShowableString[TrackTitle]:
  val Key = "title"
  val MaxLength = 191
  override def apply(raw: String): TrackTitle = raw
  override def write(t: TrackTitle): String = t

opaque type TrackPointId = Long
object TrackPointId extends ShowableLong[TrackPointId]:
  override def apply(raw: Long): TrackPointId = raw
  override def write(t: TrackPointId): Long = t

opaque type TrackCanonical = String
object TrackCanonical extends ShowableString[TrackCanonical]:
  val Key = "canonical"
  override def apply(raw: String): TrackCanonical = raw
  override def write(t: TrackCanonical): String = t
  def fromName(name: TrackName): TrackCanonical = TrackCanonical(name.show)

opaque type BoatToken = String
object BoatToken extends ShowableString[BoatToken]:
  override def apply(raw: String): BoatToken = raw
  override def write(t: BoatToken): String = t

opaque type DeviceId = Long
object DeviceId extends ShowableLong[DeviceId]:
  val Key = "boat"
  override def apply(raw: Long): DeviceId = raw
  override def write(t: DeviceId): Long = t

opaque type TrackId = Long
object TrackId extends ShowableLong[TrackId]:
  override def apply(raw: Long): TrackId = raw
  override def write(t: TrackId): Long = t

opaque type PushId = Long
object PushId extends ShowableLong[PushId]:
  override def apply(raw: Long): PushId = raw
  override def write(t: PushId): Long = t

opaque type PushToken = String
object PushToken extends ShowableString[PushToken]:
  override def apply(raw: String): PushToken = raw
  override def write(t: PushToken): String = t
