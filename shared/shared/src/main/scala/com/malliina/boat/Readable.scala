package com.malliina.boat

import cats.data.NonEmptyList
import com.malliina.values.{Email, ErrorMessage, UserId}
import io.circe.{Codec, DecodingFailure}
import io.circe.generic.semiauto.deriveCodec

import scala.util.Try

case class SingleError(message: ErrorMessage, key: String)

object SingleError {
  implicit val json: Codec[SingleError] = deriveCodec[SingleError]

  def apply(message: String, key: String): SingleError = SingleError(ErrorMessage(message), key)

  def input(message: String) = apply(ErrorMessage(message), "input")
}

case class Errors(errors: NonEmptyList[SingleError]) {
  def message = errors.head.message
  def asException = new ErrorsException(this)
}

class ErrorsException(val errors: Errors) extends Exception(errors.message.message) {
  def message = errors.message
}

object Errors {
  implicit val json: Codec[Errors] = deriveCodec[Errors]

  def apply(error: SingleError): Errors = Errors(NonEmptyList.of(error))
  def apply(message: String): Errors = apply(message, "generic")
  def apply(e: ErrorMessage): Errors = apply(e.message)
  def apply(message: String, key: String): Errors = apply(SingleError(message, key))

  // TODO improve
  def fromJson(error: DecodingFailure): Errors =
    Errors(SingleError.input(s"JSON error. $error"))
  //    json.errors.flatMap {
  //      case (path, errors) =>
  //        errors.map { error =>
  //          SingleError.input(s"${error.message} at $path.")
  //        }
  //    }.toList.toNel.map(es => Errors(es)).getOrElse {
  //      Errors(SingleError.input("JSON error."))
  //    }
}

trait Readable[T] {
  def apply(s: Option[String]): Either[Errors, T]
  def map[U](f: T => U): Readable[U] =
    (s: Option[String]) => Readable.this.apply(s).map(f)
  def flatMap[U](f: T => Either[Errors, U]): Readable[U] =
    (s: Option[String]) => Readable.this.apply(s).flatMap(f)
}

object Readable {
  implicit val string: Readable[String] = (opt: Option[String]) =>
    opt.toRight(Errors("Missing key."))
  implicit val long: Readable[Long] = string.flatMap { s =>
    Try(s.toLong).fold(err => Left(Errors(s"Invalid long: '$s'.")), l => Right(l))
  }
  implicit val int: Readable[Int] = string.flatMap { s =>
    Try(s.toInt).fold(err => Left(Errors(s"Invalid long: '$s'.")), l => Right(l))
  }
  implicit val boolean: Readable[Boolean] = string.flatMap {
    case "true"  => Right(true)
    case "false" => Right(false)
    case other   => Left(Errors(s"Invalid boolean: '$other'."))
  }
  implicit val device: Readable[DeviceId] = from[Long, DeviceId](DeviceId.build)
  implicit val userId: Readable[UserId] = from[Long, UserId](UserId.build)
  implicit val email: Readable[Email] = from[String, Email](Email.build)
  implicit val trackTitle: Readable[TrackTitle] = from[String, TrackTitle](TrackTitle.build)

  implicit def option[T: Readable]: Readable[Option[T]] = (opt: Option[String]) =>
    opt.fold[Either[Errors, Option[T]]](Right(None))(str =>
      Readable[T].apply(Option(str)).map(t => Option(t))
    )

  def apply[T](implicit r: Readable[T]): Readable[T] = r

  def from[T, U](build: T => Either[ErrorMessage, U])(implicit tr: Readable[T]) =
    tr.flatMap(t => build(t).left.map(Errors(_)))
}
