package com.malliina.config

import com.malliina.values.ErrorMessage
import com.typesafe.config.Config

implicit class ConfigOps(c: Config) extends AnyVal:
  def read[T](key: String)(implicit r: ConfigReadable[T]): Either[ErrorMessage, T] =
    r.read(key, c)
  def unsafe[T: ConfigReadable](key: String): T =
    c.read[T](key).fold(err => throw IllegalArgumentException(err.message), identity)
