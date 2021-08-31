package com.malliina.boat.http4s

import com.malliina.boat.{Errors, Readable}
import org.http4s.UrlForm

class FormReader(form: UrlForm) {
  def read[T](key: String, build: Option[String] => Either[Errors, T]): Either[Errors, T] =
    build(form.getFirst(key))

  def readT[T](key: String)(implicit r: Readable[T]): Either[Errors, T] =
    read[T](key, s => r(s))
}
