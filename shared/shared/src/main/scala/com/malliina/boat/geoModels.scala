package com.malliina.boat

import play.api.libs.json.{JsError, Json, Reads, Writes}

case class LineGeometry(`type`: String, coordinates: Seq[Coord]) extends Geometry(LineGeometry.LineString) {
  override def updateCoords(coords: Seq[Coord]): LineGeometry =
    copy(coordinates = coordinates ++ coords)

  override def coords: Seq[Coord] = coordinates
}

object LineGeometry {
  val LineString = "LineString"
  implicit val coord = Coord.jsonArray
  implicit val json = Json.format[LineGeometry]
}

case class PointGeometry(`type`: String, coordinates: Coord) extends Geometry(PointGeometry.Point) {
  override def updateCoords(coords: Seq[Coord]): PointGeometry =
    PointGeometry(`type`, coords.headOption.getOrElse(coordinates))

  override def coords: Seq[Coord] = Seq(coordinates)
}

object PointGeometry {
  val Point = "Point"
  implicit val coord = Coord.jsonArray
  implicit val json = Json.format[PointGeometry]
}

sealed abstract class Geometry(typeName: String) {
  def updateCoords(coords: Seq[Coord]): Geometry

  def coords: Seq[Coord]
}

object Geometry {
  val Type = "type"
  val all = Seq(LineGeometry, PointGeometry)
  implicit val writer = Writes[Geometry] {
    case lg@LineGeometry(_, _) => Json.toJson(lg)
    case pg@PointGeometry(_, _) => Json.toJson(pg)
  }
  implicit val reader = Reads[Geometry] { json =>
    (json \ Geometry.Type).validate[String].flatMap {
      case LineGeometry.LineString => LineGeometry.json.reads(json)
      case PointGeometry.Point => PointGeometry.json.reads(json)
      case other => JsError(s"Unsupported geometry type: '$other'.")
    }
  }
}

case class Feature(`type`: String, geometry: Geometry) {
  def addCoords(coords: Seq[Coord]) = copy(geometry = geometry.updateCoords(coords))
}

object Feature {
  implicit val json = Json.format[Feature]
}

case class FeatureCollection(`type`: String, features: Seq[Feature]) {
  def addCoords(coords: Seq[Coord]) = copy(features = features.map(_.addCoords(coords)))
}

object FeatureCollection {
  implicit val json = Json.format[FeatureCollection]
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

case class Paint(`line-color`: String, `line-width`: Int)

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
