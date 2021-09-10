package com.malliina.boat.parsing

trait SentenceAggregator[S, T, U]:
  protected var coords: Map[S, List[T]] = Map.empty

  def completed(boat: S): List[U] =
    val emittable = complete(boat, coords.getOrElse(boat, Nil))
    if emittable.nonEmpty then coords = coords.updated(boat, Nil)
    emittable

  def complete(boat: S, buffer: List[T]): List[U]
