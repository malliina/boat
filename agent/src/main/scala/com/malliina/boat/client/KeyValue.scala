package com.malliina.boat.client

import cats.Show
import cats.syntax.show.toShow

import scala.language.implicitConversions

case class KeyValue(key: String, value: String)

object KeyValue:
  given Conversion[(String, String), KeyValue] = t => (t._1, t._2)

  def build[T: Show](key: String, value: T): KeyValue = KeyValue(key, value.show)
