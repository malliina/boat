package com.malliina.geo

import com.malliina.values.{ErrorMessage, Readable, ValidatingCompanion}
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax.EncoderOps
import io.circe.{Codec, Decoder, Encoder}

import scala.math.Ordering.Double.TotalOrdering

abstract class ValidatedDouble[T](implicit
  d: Decoder[Double],
  e: Encoder[Double],
  r: Readable[Double]
) extends ValidatingCompanion[Double, T]()(using d, e, TotalOrdering, r):
  extension (t: T) def value: Double = write(t)
  def fromString(s: String) =
    s.toDoubleOption.toRight(ErrorMessage(s"Not a double: '$s'.")).flatMap(build)

/** Latitude in decimal degrees.
  *
  * @param lat
  *   latitude aka y
  */
opaque type Latitude = Double

object Latitude extends ValidatedDouble[Latitude]:
  override def build(input: Double): Either[ErrorMessage, Latitude] =
    if input >= -90 && input <= 90 then Right(input)
    else Left(ErrorMessage(s"Invalid latitude: '$input'. Must be between -90 and 90."))
  override def write(t: Latitude): Double = t
  extension (lat: Latitude) def lat: Double = lat

/** Longitude in decimal degrees.
  *
  * @param lng
  *   longitude aka x
  */
opaque type Longitude = Double

object Longitude extends ValidatedDouble[Longitude]:
  override def build(input: Double): Either[ErrorMessage, Longitude] =
    if input >= -180 && input <= 180 then Right(input)
    else Left(ErrorMessage(s"Invalid longitude: '$input'. Must be between -180 and 180."))
  override def write(t: Longitude): Double = t
  extension (lng: Longitude) def lng: Double = lng

opaque type CoordHash = String

object CoordHash:
  def fromString(s: String): CoordHash = s
  def from(c: Coord): CoordHash = c.approx

  extension (ch: CoordHash) def hash: String = ch

case class Coord(lng: Longitude, lat: Latitude):
  override def toString = s"($lng, $lat)"

  def toArray: Array[Double] = Array(lng.lng, lat.lat)

  def approx: String =
    val lngStr = Coord.format(lng.lng)
    val latStr = Coord.format(lat.lat)
    s"$lngStr,$latStr"

  val hash: CoordHash = CoordHash.from(this)

object Coord:
  val Key = "coord"

  given json: Codec[Coord] = deriveCodec[Coord]
  // GeoJSON format
  val jsonArray: Codec[Coord] = Codec.from(
    Decoder[List[Double]].emap:
      case lng :: lat :: _ =>
        build(lng, lat).left.map(_.message)
      case other =>
        Left(
          s"Expected a JSON array of at least two numbers for coordinates [lng, lat]. Got: '$other'."
        )
    ,
    (c: Coord) => c.toArray.toList.asJson
  )

  def buildOrFail(lng: Double, lat: Double): Coord =
    build(lng, lat).fold(err => throw new Exception(err.message), identity)

  def build(lng: Double, lat: Double): Either[ErrorMessage, Coord] =
    for
      longitude <- Longitude.build(lng)
      latitude <- Latitude.build(lat)
    yield Coord(longitude, latitude)

  def format(d: Double): String =
    val trunc = (d * 100000).toInt.toDouble / 100000
    "%1.5f".format(trunc).replace(',', '.')
