package com.malliina.boat

import com.malliina.geo.Coord
import com.malliina.measure.{DistanceDoubleM, DistanceLongM, DistanceM}

import scala.annotation.tailrec

object Earth extends Geo(6371.kilometers)

/** Uses the haversine formula for distances.
  *
  * @param radius
  *   in kilometers
  */
abstract class Geo(radius: DistanceM):
  def length(coords: List[Coord]): DistanceM = lengthAcc(coords, DistanceM.zero)

  @tailrec
  private def lengthAcc(coords: List[Coord], acc: DistanceM): DistanceM =
    coords match
      case head :: tail :: rest => lengthAcc(tail :: rest, acc + distance(head, tail))
      case _                    => acc

  import math.*

  /** @return
    *   the distance between the given coordinates, using the haversine formula
    * @see
    *   https://stackoverflow.com/a/16794680, https://en.wikipedia.org/wiki/Haversine_formula
    */
  def distance(c1: Coord, c2: Coord): DistanceM =
    val latDistance = toRadians(c2.lat.value - c1.lat.value)
    val lonDistance = toRadians(c2.lng.value - c1.lng.value)
    val a =
      haversine(latDistance) + cos(toRadians(c1.lat.value)) * cos(
        toRadians(c2.lat.value)
      ) * haversine(
        lonDistance
      )
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    (c * radius.toKilometers).kilometers

  private def haversine(d: Double) = pow(sin(d / 2), 2)
