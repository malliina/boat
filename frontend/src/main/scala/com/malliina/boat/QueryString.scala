package com.malliina.boat

import org.scalajs.dom.{URLSearchParams, window}

object QueryString:
  def parse = QueryString(URLSearchParams(window.location.search))

class QueryString(val inner: URLSearchParams):
  def get(name: String) = Option(inner.get(name))
  def getAll(name: String) = Option(inner.getAll(name)).map(_.toList).getOrElse(Nil)
  def contains(name: String) = get(name).nonEmpty
