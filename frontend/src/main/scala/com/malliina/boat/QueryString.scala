package com.malliina.boat

import cats.Show
import org.scalajs.dom.{URLSearchParams, window}

import scala.scalajs.js.Date

trait Printable:
  def print: String

object Printable:
  given Show[Printable] = (t: Printable) => t.print
  given [T: Show]: Conversion[T, Printable] = (t: T) =>
    new Printable:
      override def print: String = Show[T].show(t)
  given Conversion[Date, Printable] = (d: Date) =>
    new Printable:
      override def print: String = d.toISOString()
  given [A, B](using ab: Conversion[A, B]): Conversion[Option[A], Option[B]] = (optA: Option[A]) =>
    optA.map: a =>
      ab(a)

object QueryString:
  def parse = QueryString(URLSearchParams(window.location.search))
  def transact(map: (String, Option[Printable])*): Unit =
    QueryString.parse.transact(map.toMap)

class QueryString(val inner: URLSearchParams):
  def get(name: String): Option[String] =
    Option(inner.get(name))
  def getAll(name: String): Seq[String] =
    Option(inner.getAll(name)).map(_.toList).getOrElse(Nil)
  def contains(name: String): Boolean =
    get(name).nonEmpty
  def set[V: Show](name: String, value: V): Unit =
    inner.set(name, Show[V].show(value))
  def delete(name: String): Unit =
    inner.delete(name)
  def update[V: Show](name: String, value: Option[V]): Unit =
    value.fold(delete(name))(v => set(name, v))
  def transact(map: Map[String, Option[Printable]]): Unit =
    map.foreach: (k, v) =>
      v.fold(delete(k))(value => set(k, value.print))
    commit()
  def render = inner.toString
  def isEmpty = inner.isEmpty
  override def toString = render
  // This was from some google blog. Crazy api.
  def commit(): Unit = window.history.replaceState(
    "",
    "",
    s"${window.location.pathname}?$render${window.location.hash}"
  )
