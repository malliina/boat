package com.malliina.boat.http4s

import com.malliina.boat.Errors
import com.malliina.values.ErrorMessage
import org.http4s.UrlForm

class FormReader(form: UrlForm) {
  def readOne[T](key: String, build: String => Either[ErrorMessage, T]) =
    read[T](key, s => build(s).left.map(e => Errors(e)))

  def read[T](key: String, build: String => Either[Errors, T]) =
    form.getFirst(key).toRight(Errors(s"Missing '$key'.")).flatMap(build)
}
