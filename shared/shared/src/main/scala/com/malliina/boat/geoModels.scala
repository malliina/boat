package com.malliina.boat

import com.malliina.values._
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

  def apply(coordinates: Seq[Seq[Coord]]): MultiLineGeometry =
    MultiLineGeometry(Key, coordinates)
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

case class PointGeometry(`type`: String, coordinates: Coord) extends Geometry(PointGeometry.Key) {
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

case class MultiPolygon(`type`: String, coordinates: Seq[Seq[Seq[Coord]]])
  extends Geometry(MultiPolygon.Key) {
  override type Self = MultiPolygon

  override def updateCoords(coords: Seq[Coord]): MultiPolygon = copy(
    coordinates = coordinates :+ Seq(coords)
  )

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
    case lg @ LineGeometry(_, _)       => Json.toJson(lg)
    case mlg @ MultiLineGeometry(_, _) => Json.toJson(mlg)
    case pg @ PointGeometry(_, _)      => Json.toJson(pg)
    case mp @ MultiPolygon(_, _)       => Json.toJson(mp)
    case p @ Polygon(_, _)             => Json.toJson(p)
  }
  implicit val reader = Reads[Geometry] { json =>
    (json \ Geometry.Type).validate[String].flatMap {
      case LineGeometry.Key      => LineGeometry.json.reads(json)
      case MultiLineGeometry.Key => MultiLineGeometry.json.reads(json)
      case PointGeometry.Key     => PointGeometry.json.reads(json)
      case MultiPolygon.Key      => MultiPolygon.json.reads(json)
      case Polygon.Key           => Polygon.json.reads(json)
      case other                 => JsError(s"Unsupported geometry type '$other'. JSON was '$json'.")
    }
  }
}

case class LineLayout(`line-join`: String, `line-cap`: String) extends Layout

object LineLayout {
  implicit val json = Json.format[LineLayout]

  def round = LineLayout("round", "round")
}

sealed abstract class IconRotationAlignment(val value: String) extends WrappedString

object IconRotationAlignment extends StringEnumCompanion[IconRotationAlignment] {
  val all = Seq(Map, Viewport, Auto)

  override def write(t: IconRotationAlignment) = t.value

  case object Map extends IconRotationAlignment("map")
  case object Viewport extends IconRotationAlignment("viewport")
  case object Auto extends IconRotationAlignment("auto")
}

case class ImageLayout(
  `icon-image`: String,
  `icon-size`: Int,
  `icon-rotate`: Option[Seq[String]] = None,
  `icon-rotation-alignment`: IconRotationAlignment = IconRotationAlignment.Map
) extends Layout

object ImageLayout {
  implicit val json = Json.format[ImageLayout]

  val IconRotate = "icon-rotate"
}

case class OtherLayout(data: JsObject) extends Layout

object OtherLayout {
  implicit val json = Format[OtherLayout](
    Reads[OtherLayout] { json =>
      json.validate[JsObject].map(apply)
    },
    Writes[OtherLayout] { l =>
      Json.toJson(l.data)
    }
  )
}

sealed trait Layout

object Layout {
  implicit val reader = Reads[Layout] { json =>
    LineLayout.json
      .reads(json)
      .orElse(ImageLayout.json.reads(json))
      .orElse(OtherLayout.json.reads(json))
  }
  implicit val writer = Writes[Layout] {
    case ll @ LineLayout(_, _)        => Json.toJson(ll)
    case il @ ImageLayout(_, _, _, _) => Json.toJson(il)
    case OtherLayout(data)            => data
  }
}

sealed trait PropertyValue
object PropertyValue {
  implicit val format = Format[PropertyValue](
    Reads { json =>
      json.validate[String].map(Literal.apply).orElse(json.validate[JsArray].map(Custom.apply))
    },
    Writes {
      case Literal(value) => JsString(value)
      case Custom(value)  => value
    }
  )

  case class Literal(value: String) extends PropertyValue
  case class Custom(value: JsArray) extends PropertyValue
}

case class CirclePaint(`circle-radius`: Int, `circle-color`: String) extends BasePaint

object CirclePaint {
  implicit val json = Json.format[CirclePaint]
}

case class LinePaint(
  `line-color`: PropertyValue,
  `line-width`: Int,
  `line-opacity`: Double,
  `line-gap-width`: Double = 0,
  `line-dasharray`: Option[List[Int]] = None
) extends BasePaint

object LinePaint {
  implicit val json = Json.format[LinePaint]
  val blackColor = PropertyValue.Literal("#000")
  val darkGray = "#A9A9A9"

  def thin(color: PropertyValue = blackColor) = LinePaint(color, 1, 1)

  def dashed(color: PropertyValue = blackColor) =
    LinePaint(color, 1, 1, `line-dasharray` = Option(List(2, 4)))
}

// Bailout
case class OtherPaint(data: JsObject) extends BasePaint

sealed trait BasePaint

object BasePaint {
  implicit val reader = Reads[BasePaint] { json =>
    LinePaint.json
      .reads(json)
      .orElse(CirclePaint.json.reads(json))
      .orElse(json.validate[JsObject].map(OtherPaint.apply))
  }
  implicit val writer = Writes[BasePaint] {
    case lp @ LinePaint(_, _, _, _, _) => Json.toJson(lp)
    case cp @ CirclePaint(_, _)        => Json.toJson(cp)
    case OtherPaint(data)              => data
  }
}

/**
  * @see https://www.mapbox.com/mapbox-gl-js/style-spec/#layer-type
  */
sealed abstract class LayerType(val name: String)

object LayerType extends ValidatingCompanion[String, LayerType] {
  val all = Seq(Background, Fill, Line, Symbol, Raster, Circle, FillExtrusion, HeatMap, HillShade)

  override def build(input: String): Either[ErrorMessage, LayerType] =
    all
      .find(_.name.toLowerCase == input.toLowerCase)
      .toRight(ErrorMessage(s"Unknown layer type: '$input'."))

  override def write(t: LayerType): String = t.name

  case object Background extends LayerType("background")
  case object Fill extends LayerType("fill")
  case object Line extends LayerType("line")
  case object Symbol extends LayerType("symbol")
  case object Raster extends LayerType("raster")
  case object Circle extends LayerType("circle")
  case object FillExtrusion extends LayerType("fill-extrusion")
  case object HeatMap extends LayerType("heatmap")
  case object HillShade extends LayerType("hillshade")
}

sealed trait LayerSource

object LayerSource {
  val reader = Reads[LayerSource] { json =>
    StringLayerSource.json
      .reads(json)
      .orElse(InlineLayerSource.json.reads(json))
  }
  val writer = Writes[LayerSource] {
    case sls @ StringLayerSource(_)    => StringLayerSource.json.writes(sls)
    case ils @ InlineLayerSource(_, _) => InlineLayerSource.json.writes(ils)
  }
  implicit val json = Format[LayerSource](reader, writer)
}

/**
  * @see https://www.mapbox.com/mapbox-gl-js/style-spec/#layers
  */
case class Layer(
  id: String,
  `type`: LayerType,
  source: LayerSource,
  layout: Option[Layout],
  paint: Option[BasePaint],
  minzoom: Option[Double] = None,
  maxzoom: Option[Double] = None
)

object Layer {
  implicit val json = Json.format[Layer]

  def line(
    id: String,
    data: FeatureCollection,
    paint: BasePaint = LinePaint.thin(),
    minzoom: Option[Double] = None
  ) =
    Layer(
      id,
      LayerType.Line,
      InlineLayerSource(data),
      Option(LineLayout.round),
      Option(paint),
      minzoom
    )

  def symbol(id: String, data: FeatureCollection, layout: ImageLayout) =
    Layer(
      id,
      LayerType.Symbol,
      InlineLayerSource(data),
      Option(layout),
      None,
      None,
      None
    )
}

case class Feature(
  `type`: String,
  geometry: Geometry,
  properties: Map[String, JsValue],
  layer: Option[Layer]
) {
  def addCoords(coords: Seq[Coord]): Feature = copy(
    geometry = geometry.updateCoords(coords)
  )

  def props = JsObject(properties)
}

object Feature {
  val Key = "Feature"
  implicit val json = Json.format[Feature]

  def point[W](coord: Coord, props: W)(implicit w: OWrites[W]): Feature =
    Feature(PointGeometry(coord), w.writes(props).value.toMap)

  def point(coord: Coord): Feature = Feature(PointGeometry(coord), Map.empty)

  def line(coords: Seq[Coord]): Feature =
    Feature(LineGeometry(coords), Map.empty)

  def apply(geometry: Geometry, properties: Map[String, JsValue]): Feature =
    Feature(Key, geometry, properties, None)
}

case class FeatureCollection(`type`: String, features: Seq[Feature]) {
  def addCoords(coords: Seq[Coord]): FeatureCollection = copy(
    features = features.map(_.addCoords(coords))
  )
}

object FeatureCollection {
  val Key = "FeatureCollection"
  implicit val json = Json.format[FeatureCollection]

  def apply(fs: Seq[Feature]): FeatureCollection = FeatureCollection(Key, fs)
}

case class StringLayerSource(source: String) extends WrappedString with LayerSource {
  override def value = source
}

object StringLayerSource extends StringCompanion[StringLayerSource]

case class InlineLayerSource(`type`: String, data: FeatureCollection) extends LayerSource

object InlineLayerSource {

  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  // Recursive data structure:
  // FeatureCollection -> Feature -> Layer -> LayerSource -> FeatureCollection
  implicit val json: OFormat[InlineLayerSource] = (
    (JsPath \ "type").format[String] and
      (JsPath \ "data").lazyFormat[FeatureCollection](FeatureCollection.json)
  )(InlineLayerSource.apply, unlift(InlineLayerSource.unapply))

  def apply(data: FeatureCollection): InlineLayerSource =
    InlineLayerSource("geojson", data)
}

sealed trait Outcome

object Outcome {
  case object Added extends Outcome
  case object Updated extends Outcome
  case object Noop extends Outcome
}
