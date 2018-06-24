package com.malliina.boat

import com.malliina.measure.{Distance, DistanceDouble}

object Earth extends Geo(6371L.kilometers)

/** Uses the haversine formula for distances.
  *
  * @param radius in kilometers
  */
abstract class Geo(radius: Distance) {
  def length(coords: List[Coord]): Distance = lengthAcc(coords, Distance.zero)

  private def lengthAcc(coords: List[Coord], acc: Distance): Distance = {
    coords match {
      case head :: tail :: rest => lengthAcc(tail :: rest, acc + distance(head, tail))
      case _ => acc
    }
  }

  import math._

  /**
    * @return the distance between the given parameters, using the haversine formula
    * @see https://stackoverflow.com/a/16794680, https://en.wikipedia.org/wiki/Haversine_formula
    */
  def distance(c1: Coord, c2: Coord): Distance = {
    val latDistance = toRadians(c2.lat - c1.lat)
    val lonDistance = toRadians(c2.lng - c1.lng)
    val a = haversine(latDistance) + cos(toRadians(c1.lat)) * cos(toRadians(c2.lat)) * haversine(lonDistance)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    (c * radius.toKilometers).kilometers
  }

  private def haversine(d: Double) = pow(sin(d / 2), 2)
}
