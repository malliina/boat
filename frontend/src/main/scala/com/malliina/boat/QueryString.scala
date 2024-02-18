package com.malliina.boat

import cats.Show
import cats.syntax.show.toShow
import org.scalajs.dom.{URLSearchParams, window}

object QueryString:
  def parse = QueryString(URLSearchParams(window.location.search))

class QueryString(val inner: URLSearchParams):
  def get(name: String): Option[String] =
    Option(inner.get(name))
  def getAll(name: String): Seq[String] =
    Option(inner.getAll(name)).map(_.toList).getOrElse(Nil)
  def contains(name: String): Boolean =
    get(name).nonEmpty
  def set[V: Show](name: String, value: V): Unit =
    inner.set(name, value.show)
  def delete(name: String): Unit = inner.delete(name)
  def render = inner.toString
  def isEmpty = inner.isEmpty
  override def toString = render
