package com.malliina.boat

import cats.syntax.functor._
import com.malliina.values._
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

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
  implicit val coord: Codec[Coord] = Coord.jsonArray
  implicit val json: Codec[MultiLineGeometry] = deriveCodec[MultiLineGeometry]

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
  implicit val coord: Codec[Coord] = Coord.jsonArray
  implicit val json: Codec[LineGeometry] = deriveCodec[LineGeometry]

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
  implicit val coord: Codec[Coord] = Coord.jsonArray
  implicit val json: Codec[PointGeometry] = deriveCodec[PointGeometry]

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
  implicit val json: Codec[MultiPolygon] = deriveCodec[MultiPolygon]
}

case class Polygon(`type`: String, coordinates: Seq[Seq[Coord]]) extends Geometry(Polygon.Key) {
  override type Self = Polygon

  override def updateCoords(cs: Seq[Coord]): Polygon = copy(coordinates = coordinates :+ cs)

  override def coords = coordinates.flatten
}

object Polygon {
  val Key = "Polygon"
  implicit val coord = Coord.jsonArray
  implicit val json: Codec[Polygon] = deriveCodec[Polygon]
}

sealed abstract class Geometry(val typeName: String) {
  type Self <: Geometry

  def updateCoords(coords: Seq[Coord]): Self
  def coords: Seq[Coord]
}

object Geometry {
  val Type = "type"
  val all = Seq(LineGeometry, PointGeometry)

  implicit val encoder: Encoder[Geometry] = {
    case lg @ LineGeometry(_, _)       => lg.asJson
    case mlg @ MultiLineGeometry(_, _) => mlg.asJson
    case pg @ PointGeometry(_, _)      => pg.asJson
    case mp @ MultiPolygon(_, _)       => mp.asJson
    case p @ Polygon(_, _)             => p.asJson
  }
  implicit val decoder: Decoder[Geometry] = Decoder.decodeString.at(Geometry.Type).flatMap {
    case LineGeometry.Key      => Decoder[LineGeometry].widen
    case MultiLineGeometry.Key => Decoder[MultiLineGeometry].widen
    case PointGeometry.Key     => Decoder[PointGeometry].widen
    case MultiPolygon.Key      => Decoder[MultiPolygon].widen
    case Polygon.Key           => Decoder[Polygon].widen
    case other =>
      Decoder.failed(DecodingFailure(s"Unsupported geometry type '$other'.", Nil))
  }
}

sealed abstract class IconRotationAlignment(val value: String) extends WrappedString

object IconRotationAlignment extends StringEnumCompanion[IconRotationAlignment] {
  val all = Seq(Map, Viewport, Auto)

  override def write(t: IconRotationAlignment) = t.value

  case object Map extends IconRotationAlignment("map")
  case object Viewport extends IconRotationAlignment("viewport")
  case object Auto extends IconRotationAlignment("auto")
}

case class LineLayout(`line-join`: String, `line-cap`: String) extends Layout

object LineLayout {
  implicit val json: Codec[LineLayout] = deriveCodec[LineLayout]

  def round = LineLayout("round", "round")
}

case class ImageLayout(
  `icon-image`: String,
  `icon-size`: Int,
  `icon-rotate`: Option[Seq[String]] = None,
  `icon-rotation-alignment`: IconRotationAlignment = IconRotationAlignment.Map
) extends Layout

object ImageLayout {
  implicit val json: Codec[ImageLayout] = deriveCodec[ImageLayout]

  val IconRotate = "icon-rotate"
}

case class OtherLayout(data: Json) extends Layout

object OtherLayout {
  implicit val json: Codec[OtherLayout] = Codec.from(
    Decoder.decodeJson.map(json => OtherLayout(json)),
    (l: OtherLayout) => l.data.asJson
  )
}

sealed trait Layout

object Layout {
  // https://circe.github.io/circe/codecs/adt.html#adts-encoding-and-decoding
  implicit val reader: Decoder[Layout] = List[Decoder[Layout]](
    Decoder[LineLayout].widen,
    Decoder[ImageLayout].widen,
    Decoder[OtherLayout].widen
  ).reduceLeft(_ or _)
  implicit val encoder: Encoder[Layout] = {
    case ll @ LineLayout(_, _)        => ll.asJson
    case il @ ImageLayout(_, _, _, _) => il.asJson
    case OtherLayout(data)            => data
  }
}

sealed trait PropertyValue

object PropertyValue {
  implicit val codec: Codec[PropertyValue] = Codec.from(
    Decoder.decodeString.map(Literal.apply).or(Decoder.decodeJson.map(Custom.apply)),
    {
      case Literal(value) => value.asJson
      case Custom(value)  => value
    }
  )

  case class Literal(value: String) extends PropertyValue
  case class Custom(value: Json) extends PropertyValue
}

case class CirclePaint(`circle-radius`: Int, `circle-color`: String) extends BasePaint

object CirclePaint {
  implicit val json: Codec[CirclePaint] = deriveCodec[CirclePaint]
}

case class LinePaint(
  `line-color`: PropertyValue,
  `line-width`: Int,
  `line-opacity`: Double,
  `line-gap-width`: Double = 0,
  `line-dasharray`: Option[List[Int]] = None
) extends BasePaint

object LinePaint {
  implicit val json: Codec[LinePaint] = deriveCodec[LinePaint]
  val blackColor = PropertyValue.Literal("#000")
  val darkGray = "#A9A9A9"

  def thin(color: PropertyValue = blackColor) = LinePaint(color, 1, 1)

  def dashed(color: PropertyValue = blackColor) =
    LinePaint(color, 1, 1, `line-dasharray` = Option(List(2, 4)))
}

// Bailout
case class OtherPaint(data: Json) extends BasePaint

object OtherPaint {
  implicit val codec: Codec[OtherPaint] = Codec.from(
    Decoder.decodeJson.map(json => OtherPaint(json)),
    Encoder.encodeJson.contramap(t => t.data)
  )
}

sealed trait BasePaint

object BasePaint {
  implicit val reader: Decoder[BasePaint] =
    List[Decoder[BasePaint]](
      Decoder[LinePaint].widen,
      Decoder[CirclePaint].widen,
      Decoder[OtherPaint].widen
    ).reduceLeft(_ or _)
  implicit val writer: Encoder[BasePaint] = {
    case lp @ LinePaint(_, _, _, _, _) => lp.asJson
    case cp @ CirclePaint(_, _)        => cp.asJson
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
  implicit val reader: Decoder[LayerSource] =
    List[Decoder[LayerSource]](Decoder[StringLayerSource].widen, Decoder[InlineLayerSource].widen)
      .reduceLeft(_ or _)
  implicit val writer: Encoder[LayerSource] = {
    case sls @ StringLayerSource(_)    => sls.asJson
    case ils @ InlineLayerSource(_, _) => ils.asJson
  }
  implicit val json: Codec[LayerSource] = Codec.from[LayerSource](reader, writer)
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
  implicit val json: Codec[Layer] = deriveCodec[Layer]

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
  properties: Map[String, Json],
  layer: Option[Layer]
) {
  def addCoords(coords: Seq[Coord]): Feature = copy(
    geometry = geometry.updateCoords(coords)
  )

  def props = properties.asJson
}

object Feature {
  val Key = "Feature"
  implicit val json: Codec[Feature] = deriveCodec[Feature]

  def point[W](coord: Coord, props: W)(implicit w: Encoder[W]): Feature =
    Feature(PointGeometry(coord), props.asJson.asObject.map(_.toMap).getOrElse(Map.empty))

  def point(coord: Coord): Feature = Feature(PointGeometry(coord), Map.empty)

  def line(coords: Seq[Coord]): Feature =
    Feature(LineGeometry(coords), Map.empty)

  def apply(geometry: Geometry, properties: Map[String, Json]): Feature =
    Feature(Key, geometry, properties, None)
}

case class FeatureCollection(`type`: String, features: Seq[Feature]) {
  def addCoords(coords: Seq[Coord]): FeatureCollection = copy(
    features = features.map(_.addCoords(coords))
  )
}

object FeatureCollection {
  val Key = "FeatureCollection"
  implicit val json: Codec[FeatureCollection] = deriveCodec[FeatureCollection]

  def apply(fs: Seq[Feature]): FeatureCollection = FeatureCollection(Key, fs)
}

case class StringLayerSource(source: String) extends WrappedString with LayerSource {
  override def value = source
}

object StringLayerSource extends StringCompanion[StringLayerSource]

case class InlineLayerSource(`type`: String, data: FeatureCollection) extends LayerSource

object InlineLayerSource {
  // Recursive data structure:
  // FeatureCollection -> Feature -> Layer -> LayerSource -> FeatureCollection
  implicit val json: Codec[InlineLayerSource] = deriveCodec[InlineLayerSource]

  def apply(data: FeatureCollection): InlineLayerSource =
    InlineLayerSource("geojson", data)
}

sealed trait Outcome

object Outcome {
  case object Added extends Outcome
  case object Updated extends Outcome
  case object Noop extends Outcome
}
