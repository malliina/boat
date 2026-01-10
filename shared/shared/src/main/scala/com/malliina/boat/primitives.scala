package com.malliina.boat

import cats.Show
import cats.implicits.toShow
import com.malliina.values.{ErrorMessage, Readable, ValidatedLong, ValidatedString, ValidatingCompanion}
import com.malliina.values.Literals.err
import io.circe.{Codec, Decoder, Encoder}
import org.typelevel.ci.CIString

abstract class CICompanion[T] extends ValidatingCompanion[CIString, T]:
  given Show[T] = Show.show(t => write(t).toString)

given Codec[CIString] = Codec.from(
  Decoder.decodeString.map(s => CIString(s)),
  Encoder.encodeString.contramap[CIString](_.toString)
)
given Readable[CIString] = Readable.string.map(s => CIString(s))

opaque type BoatName = CIString
object BoatName extends CICompanion[BoatName]:
  val Key = "boatName"
  override def build(input: CIString): Either[ErrorMessage, BoatName] = Right(input)
  override def write(t: BoatName): CIString = t

opaque type TrackName = String
object TrackName extends ValidatedString[TrackName]:
  val Key = "track"
  override def build(input: String): Either[ErrorMessage, TrackName] = Right(input)
  override def write(t: TrackName): String = t

opaque type TrackTitle = String
object TrackTitle extends ValidatedString[TrackTitle]:
  val Key = "title"
  val MaxLength = 191
  override def build(input: String): Either[ErrorMessage, TrackTitle] = Right(input)
  override def write(t: TrackTitle): String = t

opaque type TrackPointId = Long
object TrackPointId extends ValidatedLong[TrackPointId]:
  override def build(input: Long): Either[ErrorMessage, TrackPointId] = Right(input)
  override def write(t: TrackPointId): Long = t

opaque type TrackCanonical = String
object TrackCanonical extends ValidatedString[TrackCanonical]:
  val Key = "canonical"
  override def build(input: String): Either[ErrorMessage, TrackCanonical] = Right(input)
  override def write(t: TrackCanonical): String = t
  def fromName(name: TrackName): TrackCanonical = name.show

opaque type BoatToken = String
object BoatToken extends ValidatedString[BoatToken]:
  override def build(input: String): Either[ErrorMessage, BoatToken] = Right(input)
  override def write(t: BoatToken): String = t

opaque type DeviceId = Long
object DeviceId extends ValidatedLong[DeviceId]:
  override def build(input: Long): Either[ErrorMessage, DeviceId] = Right(input)
  val Key = "boat"
  override def write(t: DeviceId): Long = t

opaque type TrackId = Long
object TrackId extends ValidatedLong[TrackId]:
  override def build(input: Long): Either[ErrorMessage, TrackId] = Right(input)
  override def write(t: TrackId): Long = t

opaque type PushId = Long
object PushId extends ValidatedLong[PushId]:
  override def build(input: Long): Either[ErrorMessage, PushId] = Right(input)
  override def write(t: PushId): Long = t

opaque type PushToken = String
object PushToken extends ValidatedString[PushToken]:
  override def build(input: String): Either[ErrorMessage, PushToken] = Right(input)
  override def write(t: PushToken): String = t

opaque type LiveActivityId = String
object LiveActivityId extends ValidatedString[LiveActivityId]:
  override def build(input: String): Either[ErrorMessage, LiveActivityId] =
    if input.isBlank then Left(err"Input cannot be blank.")
    else Right(input)
  override def write(t: LiveActivityId): String = t

opaque type PhoneId = String
object PhoneId extends ValidatedString[PhoneId]:
  override def build(input: String): Either[ErrorMessage, PhoneId] =
    if input.isBlank then Left(err"Input cannot be blank.")
    else Right(input)
  override def write(t: PhoneId): String = t

opaque type UserAgent = String
object UserAgent extends ValidatedString[UserAgent]:
  override def build(input: String): Either[ErrorMessage, UserAgent] =
    if input.isEmpty then Left(err"Input cannot be empty.")
    else Right(input.trim.take(128))
  override def write(t: UserAgent): String = t
