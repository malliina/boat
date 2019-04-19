package com.malliina.boat

import com.malliina.measure.{DistanceDoubleM, DistanceLongM, DistanceM}

import scala.annotation.tailrec

object Earth extends Geo(6371.kilometers)

/** Uses the haversine formula for distances.
  *
  * @param radius in kilometers
  */
abstract class Geo(radius: DistanceM) {
  def length(coords: List[Coord]): DistanceM = lengthAcc(coords, DistanceM.zero)

  @tailrec
  private def lengthAcc(coords: List[Coord], acc: DistanceM): DistanceM = {
    coords match {
      case head :: tail :: rest => lengthAcc(tail :: rest, acc + distance(head, tail))
      case _                    => acc
    }
  }

  import math._

  /**
    * @return the distance between the given coordinates, using the haversine formula
    * @see https://stackoverflow.com/a/16794680, https://en.wikipedia.org/wiki/Haversine_formula
    */
  def distance(c1: Coord, c2: Coord): DistanceM = {
    val latDistance = toRadians(c2.lat.lat - c1.lat.lat)
    val lonDistance = toRadians(c2.lng.lng - c1.lng.lng)
    val a = haversine(latDistance) + cos(toRadians(c1.lat.lat)) * cos(toRadians(c2.lat.lat)) * haversine(
      lonDistance)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    (c * radius.toKilometers).kilometers
  }

  private def haversine(d: Double) = pow(sin(d / 2), 2)
}
