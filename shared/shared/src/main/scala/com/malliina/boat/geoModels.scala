package com.malliina.boat

import play.api.libs.json._

case class MultiLineGeometry(`type`: String, coordinates: Seq[Seq[Coord]])
  extends Geometry(MultiLineGeometry.Key) {

  override def updateCoords(coords: Seq[Coord]): MultiLineGeometry =
    copy(coordinates = coordinates :+ coords)

  override def coords: Seq[Coord] = coordinates.flatten

  def toLine = LineGeometry(`type`, coords)
}

object MultiLineGeometry {
  val Key = "MultiLineString"
  implicit val coord: Format[Coord] = Coord.jsonArray
  implicit val json = Json.format[MultiLineGeometry]
}

case class LineGeometry(`type`: String, coordinates: Seq[Coord])
  extends Geometry(LineGeometry.Key) {

  override def updateCoords(coords: Seq[Coord]): LineGeometry =
    copy(coordinates = coordinates ++ coords)

  override def coords: Seq[Coord] = coordinates
}

object LineGeometry {
  val Key = "LineString"
  implicit val coord = Coord.jsonArray
  implicit val json = Json.format[LineGeometry]

  def apply(coords: Seq[Coord]): LineGeometry = LineGeometry(Key, coords)
}

case class PointGeometry(`type`: String, coordinates: Coord)
  extends Geometry(PointGeometry.Key) {

  override def updateCoords(coords: Seq[Coord]): PointGeometry =
    PointGeometry(`type`, coords.headOption.getOrElse(coordinates))

  override def coords: Seq[Coord] = Seq(coordinates)
}

object PointGeometry {
  val Key = "Point"
  implicit val coord = Coord.jsonArray
  implicit val json = Json.format[PointGeometry]

  def apply(point: Coord): PointGeometry = PointGeometry(Key, point)
}

sealed abstract class Geometry(val typeName: String) {
  def updateCoords(coords: Seq[Coord]): Geometry

  def coords: Seq[Coord]
}

object Geometry {
  val Type = "type"
  val all = Seq(LineGeometry, PointGeometry)
  implicit val writer = Writes[Geometry] {
    case lg@LineGeometry(_, _) => Json.toJson(lg)
    case mlg@MultiLineGeometry(_, _) => Json.toJson(mlg)
    case pg@PointGeometry(_, _) => Json.toJson(pg)
  }
  implicit val reader = Reads[Geometry] { json =>
    (json \ Geometry.Type).validate[String].flatMap {
      case LineGeometry.Key => LineGeometry.json.reads(json)
      case MultiLineGeometry.Key => MultiLineGeometry.json.reads(json)
      case PointGeometry.Key => PointGeometry.json.reads(json)
      case other => JsError(s"Unsupported geometry type: '$other'.")
    }
  }
}

case class Feature(`type`: String, geometry: Geometry, properties: Map[String, JsValue], layer: Option[JsObject]) {
  def addCoords(coords: Seq[Coord]): Feature = copy(
    geometry = geometry.updateCoords(coords)
  )
}

object Feature {
  val Key = "Feature"
  implicit val json = Json.format[Feature]

  def apply(geometry: Geometry, properties: Map[String, JsValue]): Feature =
    Feature(Key, geometry, properties, None)
}

case class FeatureCollection(`type`: String, features: Seq[Feature]) {
  def addCoords(coords: Seq[Coord]): FeatureCollection = copy(features = features.map(_.addCoords(coords)))
}

object FeatureCollection {
  val Key = "FeatureCollection"
  implicit val json = Json.format[FeatureCollection]

  def apply(fs: Seq[Feature]): FeatureCollection = FeatureCollection(Key, fs)
}

case class AnimationSource(`type`: String, data: FeatureCollection)

object AnimationSource {
  implicit val json = Json.format[AnimationSource]
}

case class LineLayout(`line-join`: String, `line-cap`: String) extends Layout

object LineLayout {
  implicit val json = Json.format[LineLayout]
}

case class ImageLayout(`icon-image`: String, `icon-size`: Int) extends Layout

object ImageLayout {
  implicit val json = Json.format[ImageLayout]
}

sealed trait Layout

object Layout {
  implicit val writer = Writes[Layout] {
    case ll@LineLayout(_, _) => Json.toJson(ll)
    case il@ImageLayout(_, _) => Json.toJson(il)
  }
}

case class Paint(`line-color`: String,
                 `line-width`: Int,
                 `line-opacity`: Double,
                 `line-gap-width`: Double = 0)

object Paint {
  implicit val json = Json.format[Paint]
}

sealed abstract class LayerType(val name: String)

object LayerType {
  implicit val writer = Writes[LayerType] { l => Json.toJson(l.name) }
}

case object LineLayer extends LayerType("line")

case object SymbolLayer extends LayerType("symbol")

case class Animation(id: String,
                     `type`: LayerType,
                     source: AnimationSource,
                     layout: Layout,
                     paint: Option[Paint])

object Animation {
  implicit val json = Json.writes[Animation]
}
