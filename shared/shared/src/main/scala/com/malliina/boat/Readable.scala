package com.malliina.boat

import cats.data.NonEmptyList
import com.comcast.ip4s.{Host, Port}
import com.malliina.values.{ErrorMessage, Readable, UserId}
import io.circe.{Codec, DecodingFailure}

case class SingleError(message: ErrorMessage, key: String) derives Codec.AsObject

object SingleError:
  val TokenExpiredKey = "token_expired"

  def apply(message: String, key: String): SingleError = SingleError(ErrorMessage(message), key)
  def input(message: String) = apply(ErrorMessage(message), "input")

case class Errors(errors: NonEmptyList[SingleError]) derives Codec.AsObject:
  def message = errors.head.message
  def asException = ErrorsException(this)

class ErrorsException(val errors: Errors) extends Exception(errors.message.message):
  def message = errors.message

object Errors:
  def apply(error: SingleError): Errors = Errors(NonEmptyList.of(error))
  def apply(message: String): Errors = apply(message, "generic")
  def apply(e: ErrorMessage): Errors = apply(e.message)
  def apply(message: String, key: String): Errors = apply(SingleError(message, key))

  // TODO improve
  def fromJson(error: DecodingFailure): Errors =
    Errors(SingleError.input(s"JSON error. $error"))

object Readables:
  given device: Readable[DeviceId] = from[Long, DeviceId](DeviceId.build)
  given userId: Readable[UserId] = from[Long, UserId](UserId.build)
  given trackTitle: Readable[TrackTitle] = from[String, TrackTitle](TrackTitle.build)
  given host: Readable[Host] =
    from[String, Host](s => Host.fromString(s).toRight(ErrorMessage(s"Invalid host: '$s'.")))
  given port: Readable[Port] =
    from[Int, Port](i => Port.fromInt(i).toRight(ErrorMessage(s"Invalid port: '$i'.")))

  def from[T, U](build: T => Either[ErrorMessage, U])(using tr: Readable[T]): Readable[U] =
    tr.emap(t => build(t))
