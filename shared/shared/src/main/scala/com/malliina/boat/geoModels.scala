package com.malliina.boat

import play.api.libs.json._

case class MultiLineGeometry(`type`: String, coordinates: Seq[Seq[Coord]])
  extends Geometry(MultiLineGeometry.Key) {
  override type Self = MultiLineGeometry

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
  type Self = LineGeometry

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
  type Self = PointGeometry

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

case class MultiPolygon(`type`: String, coordinates: Seq[Seq[Seq[Coord]]]) extends Geometry(MultiPolygon.Key) {
  override type Self = MultiPolygon

  override def updateCoords(coords: Seq[Coord]): MultiPolygon = copy(coordinates = coordinates :+ Seq(coords))

  override def coords: Seq[Coord] = coordinates.flatten.flatten
}

object MultiPolygon {
  val Key = "MultiPolygon"
  implicit val coord = Coord.jsonArray
  implicit val json = Json.format[MultiPolygon]
}

case class Polygon(`type`: String, coordinates: Seq[Seq[Coord]]) extends Geometry(Polygon.Key) {
  override type Self = Polygon

  override def updateCoords(cs: Seq[Coord]): Polygon = copy(coordinates = coordinates :+ cs)

  override def coords = coordinates.flatten
}

object Polygon {
  val Key = "Polygon"
  implicit val coord = Coord.jsonArray
  implicit val json = Json.format[Polygon]
}

sealed abstract class Geometry(val typeName: String) {
  type Self <: Geometry

  def updateCoords(coords: Seq[Coord]): Self

  def coords: Seq[Coord]
}

object Geometry {
  val Type = "type"
  val all = Seq(LineGeometry, PointGeometry)
  implicit val writer = Writes[Geometry] {
    case lg@LineGeometry(_, _) => Json.toJson(lg)
    case mlg@MultiLineGeometry(_, _) => Json.toJson(mlg)
    case pg@PointGeometry(_, _) => Json.toJson(pg)
    case mp@MultiPolygon(_, _) => Json.toJson(mp)
    case p@Polygon(_, _) => Json.toJson(p)
  }
  implicit val reader = Reads[Geometry] { json =>
    (json \ Geometry.Type).validate[String].flatMap {
      case LineGeometry.Key => LineGeometry.json.reads(json)
      case MultiLineGeometry.Key => MultiLineGeometry.json.reads(json)
      case PointGeometry.Key => PointGeometry.json.reads(json)
      case MultiPolygon.Key => MultiPolygon.json.reads(json)
      case Polygon.Key => Polygon.json.reads(json)
      case other => JsError(s"Unsupported geometry type '$other'. JSON was '$json'.")
    }
  }
}

case class Feature(`type`: String, geometry: Geometry, properties: Map[String, JsValue], layer: Option[JsObject]) {
  def addCoords(coords: Seq[Coord]): Feature = copy(
    geometry = geometry.updateCoords(coords)
  )

  def props = JsObject(properties)
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

case class LayerSource(`type`: String, data: FeatureCollection)

object LayerSource {
  implicit val json = Json.format[LayerSource]

  def apply(data: FeatureCollection): LayerSource =
    LayerSource("geojson", data)
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

case class CirclePaint(`circle-radius`: Int, `circle-color`: String) extends BasePaint

object CirclePaint {
  implicit val json = Json.format[CirclePaint]
}

case class LinePaint(`line-color`: String,
                     `line-width`: Int,
                     `line-opacity`: Double,
                     `line-gap-width`: Double = 0) extends BasePaint

object LinePaint {
  implicit val json = Json.format[LinePaint]
}

sealed trait BasePaint

object BasePaint {
  implicit val writer = Writes[BasePaint] {
    case lp@LinePaint(_, _, _, _) => Json.toJson(lp)
    case cp@CirclePaint(_, _) => Json.toJson(cp)
  }
}

sealed abstract class LayerType(val name: String)

object LayerType {
  implicit val writer = Writes[LayerType] { l => Json.toJson(l.name) }
}

case object LineLayer extends LayerType("line")

case object SymbolLayer extends LayerType("symbol")

case object CircleLayer extends LayerType("circle")

case class Layer(id: String,
                 `type`: LayerType,
                 source: LayerSource,
                 layout: Option[Layout],
                 paint: Option[BasePaint])

object Layer {
  implicit val json = Json.writes[Layer]
}
