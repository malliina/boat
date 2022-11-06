package com.malliina.boat.client

import com.malliina.boat.{Errors, SingleError}
import com.malliina.values.{ErrorMessage, Readable}
import org.http4s.UrlForm

class FormReader(form: UrlForm):
  def read[T](key: String)(implicit fr: FormReadable[T]): Either[Errors, T] =
    fr.read(key, form).left.map(err => Errors(SingleError.input(err.message)))

trait FormReadable[T]:
  def read(key: String, form: UrlForm): Either[ErrorMessage, T]

  def emap[U](f: T => Either[ErrorMessage, U]): FormReadable[U] = (key: String, form: UrlForm) =>
    read(key, form).flatMap(f)

object FormReadable:
  val string: FormReadable[String] = (key: String, form: UrlForm) =>
    form.getFirst(key).toRight(ErrorMessage(s"Not found: '$key'."))

  def apply[T](implicit fr: FormReadable[T]): FormReadable[T] = fr

  implicit def option[T](implicit r: Readable[T]): FormReadable[Option[T]] =
    (key: String, form: UrlForm) =>
      form.getFirst(key).fold(Right(None))(s => r.read(s).map(Option.apply))

  implicit def readable[T](implicit r: Readable[T]): FormReadable[T] =
    string.emap(str => r.read(str))
