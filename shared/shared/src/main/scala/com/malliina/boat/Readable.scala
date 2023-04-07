package com.malliina.boat

import cats.data.NonEmptyList
import com.malliina.values.{Email, ErrorMessage, UserId, Readable}
import io.circe.{Codec, DecodingFailure}
import io.circe.generic.semiauto.deriveCodec

import scala.util.Try

case class SingleError(message: ErrorMessage, key: String)

object SingleError:
  implicit val json: Codec[SingleError] = deriveCodec[SingleError]

  def apply(message: String, key: String): SingleError = SingleError(ErrorMessage(message), key)
  def input(message: String) = apply(ErrorMessage(message), "input")

case class Errors(errors: NonEmptyList[SingleError]):
  def message = errors.head.message
  def asException = new ErrorsException(this)

class ErrorsException(val errors: Errors) extends Exception(errors.message.message):
  def message = errors.message

object Errors:
  implicit val json: Codec[Errors] = deriveCodec[Errors]

  def apply(error: SingleError): Errors = Errors(NonEmptyList.of(error))
  def apply(message: String): Errors = apply(message, "generic")
  def apply(e: ErrorMessage): Errors = apply(e.message)
  def apply(message: String, key: String): Errors = apply(SingleError(message, key))

  // TODO improve
  def fromJson(error: DecodingFailure): Errors =
    Errors(SingleError.input(s"JSON error. $error"))

object Readables:
  implicit val string: Readable[String] = Readable.string
  implicit val long: Readable[Long] = string.emap { s =>
    Try(s.toLong).fold(err => Left(ErrorMessage(s"Invalid long: '$s'.")), l => Right(l))
  }
  implicit val int: Readable[Int] = string.emap { s =>
    Try(s.toInt).fold(err => Left(ErrorMessage(s"Invalid long: '$s'.")), l => Right(l))
  }
  implicit val boolean: Readable[Boolean] = string.emap {
    case "true"  => Right(true)
    case "false" => Right(false)
    case other   => Left(ErrorMessage(s"Invalid boolean: '$other'."))
  }
  implicit val device: Readable[DeviceId] = from[Long, DeviceId](DeviceId.build)
  implicit val userId: Readable[UserId] = from[Long, UserId](UserId.build)
  implicit val trackTitle: Readable[TrackTitle] = from[String, TrackTitle](TrackTitle.build)

  def from[T, U](build: T => Either[ErrorMessage, U])(implicit tr: Readable[T]): Readable[U] =
    tr.emap(t => build(t))
