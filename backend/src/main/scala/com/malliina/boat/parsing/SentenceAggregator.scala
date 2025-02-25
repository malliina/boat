package com.malliina.boat.parsing

import com.malliina.boat.UserAgent

trait SentenceAggregator[S, T, U]:
  protected var coords: Map[S, List[T]] = Map.empty

  def completed(boat: S, userAgent: Option[UserAgent]): List[U] =
    val emittable = complete(boat, coords.getOrElse(boat, Nil), userAgent)
    if emittable.nonEmpty then coords = coords.updated(boat, Nil)
    emittable

  def complete(boat: S, buffer: List[T], userAgent: Option[UserAgent]): List[U]
