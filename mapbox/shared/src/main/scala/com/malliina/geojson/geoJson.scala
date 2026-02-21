package com.malliina.geojson

import cats.syntax.all.toFunctorOps
import com.malliina.geo.Coord
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax.EncoderOps

case class MultiLineGeometry(`type`: String, coordinates: Seq[Seq[Coord]])
  extends Geometry(MultiLineGeometry.Key):
  override type Self = MultiLineGeometry
  override def updateCoords(coords: Seq[Coord]): MultiLineGeometry =
    copy(coordinates = coordinates :+ coords)
  override def coords: Seq[Coord] = coordinates.flatten
  def toLine = LineGeometry(`type`, coords)

object MultiLineGeometry:
  val Key = "MultiLineString"
  given coordJson: Codec[Coord] = Coord.jsonArray
  given json: Codec[MultiLineGeometry] = deriveCodec[MultiLineGeometry]

  def apply(coordinates: Seq[Seq[Coord]]): MultiLineGeometry =
    MultiLineGeometry(Key, coordinates)

case class LineGeometry(`type`: String, coordinates: Seq[Coord]) extends Geometry(LineGeometry.Key):
  type Self = LineGeometry

  override def updateCoords(coords: Seq[Coord]): LineGeometry =
    copy(coordinates = coordinates ++ coords)

  override def coords: Seq[Coord] = coordinates

object LineGeometry:
  val Key = "LineString"
  given coordJson: Codec[Coord] = Coord.jsonArray
  given json: Codec[LineGeometry] = deriveCodec[LineGeometry]

  def apply(coords: Seq[Coord]): LineGeometry = LineGeometry(Key, coords)

case class PointGeometry(`type`: String, coordinates: Coord) extends Geometry(PointGeometry.Key):
  type Self = PointGeometry

  override def updateCoords(coords: Seq[Coord]): PointGeometry =
    PointGeometry(`type`, coords.headOption.getOrElse(coordinates))

  override def coords: Seq[Coord] = Seq(coordinates)

object PointGeometry:
  val Key = "Point"
  given coordJson: Codec[Coord] = Coord.jsonArray
  given json: Codec[PointGeometry] = deriveCodec[PointGeometry]

  def apply(point: Coord): PointGeometry = PointGeometry(Key, point)

case class MultiPolygon(`type`: String, coordinates: Seq[Seq[Seq[Coord]]])
  extends Geometry(MultiPolygon.Key):
  override type Self = MultiPolygon

  override def updateCoords(coords: Seq[Coord]): MultiPolygon = copy(
    coordinates = coordinates :+ Seq(coords)
  )

  override def coords: Seq[Coord] = coordinates.flatten.flatten

object MultiPolygon:
  val Key = "MultiPolygon"
  given coordJson: Codec[Coord] = Coord.jsonArray
  given json: Codec[MultiPolygon] = deriveCodec[MultiPolygon]

case class Polygon(`type`: String, coordinates: Seq[Seq[Coord]]) extends Geometry(Polygon.Key):
  override type Self = Polygon
  override def updateCoords(cs: Seq[Coord]): Polygon = copy(coordinates = coordinates :+ cs)
  override def coords = coordinates.flatten

object Polygon:
  val Key = "Polygon"
  given coordJson: Codec[Coord] = Coord.jsonArray
  given json: Codec[Polygon] = deriveCodec[Polygon]

sealed abstract class Geometry(val typeName: String):
  type Self <: Geometry

  def updateCoords(coords: Seq[Coord]): Self
  def coords: Seq[Coord]

object Geometry:
  val Type = "type"

  given Encoder[Geometry] =
    case lg @ LineGeometry(_, _)       => lg.asJson
    case mlg @ MultiLineGeometry(_, _) => mlg.asJson
    case pg @ PointGeometry(_, _)      => pg.asJson
    case mp @ MultiPolygon(_, _)       => mp.asJson
    case p @ Polygon(_, _)             => p.asJson
  given Decoder[Geometry] = Decoder.decodeString
    .at(Geometry.Type)
    .flatMap:
      case LineGeometry.Key      => Decoder[LineGeometry].widen
      case MultiLineGeometry.Key => Decoder[MultiLineGeometry].widen
      case PointGeometry.Key     => Decoder[PointGeometry].widen
      case MultiPolygon.Key      => Decoder[MultiPolygon].widen
      case Polygon.Key           => Decoder[Polygon].widen
      case other                 =>
        Decoder.failed(DecodingFailure(s"Unsupported geometry type '$other'.", Nil))

case class Feature(
  `type`: String,
  geometry: Geometry,
  properties: Map[String, Json]
) derives Codec.AsObject:
  def addCoords(coords: Seq[Coord]): Feature = copy(
    geometry = geometry.updateCoords(coords)
  )

  def props: Json = properties.asJson

object Feature:
  val Key = "Feature"

  def point[W: Encoder](coord: Coord, props: W): Feature =
    Feature(PointGeometry(coord), props.asJson.asObject.map(_.toMap).getOrElse(Map.empty))
  def point(coord: Coord): Feature = Feature(PointGeometry(coord), Map.empty)
  def line(coords: Seq[Coord]): Feature =
    Feature(LineGeometry(coords), Map.empty)
  def apply(geometry: Geometry, properties: Map[String, Json]): Feature =
    Feature(Key, geometry, properties)

case class FeatureCollection(`type`: String, features: Seq[Feature]) derives Codec.AsObject:
  def addCoords(coords: Seq[Coord]): FeatureCollection = copy(
    features = features.map(_.addCoords(coords))
  )

object FeatureCollection:
  val Key = "FeatureCollection"

  def apply(fs: Seq[Feature]): FeatureCollection = FeatureCollection(Key, fs)
