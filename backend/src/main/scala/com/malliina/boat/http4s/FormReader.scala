package com.malliina.boat.http4s

import com.malliina.boat.{DeviceId, Errors, TrackTitle}
import com.malliina.values.{Email, ErrorMessage, UserId}
import org.http4s.UrlForm

import scala.util.Try

class FormReader(form: UrlForm) {
  def read[T](key: String, build: String => Either[Errors, T]) =
    form.getFirst(key).toRight(Errors(s"Missing '$key'.")).flatMap(build)

  def readT[T](key: String)(implicit r: Readable[T]) = read[T](key, s => r(s))
}

trait Readable[T] {
  def apply(s: String): Either[Errors, T]
  def map[U](f: T => U): Readable[U] =
    (s: String) => Readable.this.apply(s).map(f)
  def flatMap[U](f: T => Either[Errors, U]): Readable[U] =
    (s: String) => Readable.this.apply(s).flatMap(f)
}

object Readable {
  implicit val string: Readable[String] = (s: String) => Right(s)
  implicit val long: Readable[Long] = (s: String) =>
    Try(s.toLong).fold(err => Left(Errors(s"Invalid integer: '$s'.")), l => Right(l))
  implicit val boolean: Readable[Boolean] = {
    case "true"  => Right(true)
    case "false" => Right(false)
    case other   => Left(Errors(s"Invalid boolean: '$other'."))
  }
  implicit val device: Readable[DeviceId] = from[Long, DeviceId](DeviceId.build)
  implicit val userId: Readable[UserId] = from[Long, UserId](UserId.build)
  implicit val email: Readable[Email] = from[String, Email](Email.build)
  implicit val trackTitle: Readable[TrackTitle] = from[String, TrackTitle](TrackTitle.build)

  def from[T, U](build: T => Either[ErrorMessage, U])(implicit tr: Readable[T]) =
    tr.flatMap(t => build(t).left.map(Errors(_)))
}
