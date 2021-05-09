package com.malliina.boat.http4s

import com.malliina.boat.Errors
import com.malliina.values.ErrorMessage
import org.http4s.UrlForm

import scala.util.Try

class FormReader(form: UrlForm) {
  def readBool(key: String) = readOne(
    key,
    {
      case "true"  => Right(true)
      case "false" => Right(false)
      case other   => Left(ErrorMessage(s"Invalid boolean: '$other'."))
    }
  )
  def readLong[T](key: String, build: Long => Either[ErrorMessage, T]) =
    readOne[T](
      key,
      s => Try(s.toLong).fold(e => Left(ErrorMessage(s"Invalid integer: '$s'.")), i => build(i))
    )
  def readOne[T](key: String, build: String => Either[ErrorMessage, T]) =
    read[T](key, s => build(s).left.map(e => Errors(e)))

  def read[T](key: String, build: String => Either[Errors, T]) =
    form.getFirst(key).toRight(Errors(s"Missing '$key'.")).flatMap(build)
}
