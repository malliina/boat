package com.malliina.mapbox

import cats.syntax.all.toFunctorOps
import com.malliina.geo.Coord
import com.malliina.geojson.{FeatureCollection, Geometry, LineGeometry, PointGeometry}
import com.malliina.values.*
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax.EncoderOps
import io.circe.{Codec, Decoder, Encoder, Json}

enum IconRotationAlignment(val value: String):
  case Map extends IconRotationAlignment("map")
  case Viewport extends IconRotationAlignment("viewport")
  case Auto extends IconRotationAlignment("auto")

object IconRotationAlignment extends StringEnumCompanion[IconRotationAlignment]:
  val all = Seq(Map, Viewport, Auto)

  override def write(t: IconRotationAlignment): String = t.value

case class LineLayout(`line-join`: String, `line-cap`: String, `line-sort-key`: Option[Double])
  extends Layout derives Codec.AsObject

object LineLayout:
  def round = LineLayout("round", "round", `line-sort-key` = None)

case class ImageLayout(
  `icon-image`: String,
  `icon-size`: Double, // Scale, I guess
  `icon-rotate`: Option[Seq[String]] = None,
  `icon-rotation-alignment`: IconRotationAlignment = IconRotationAlignment.Map
) extends Layout derives Codec.AsObject

object ImageLayout:
  val IconRotate = "icon-rotate"

case class OtherLayout(data: Json) extends Layout

object OtherLayout:
  given Codec[OtherLayout] = Codec.from(
    Decoder.decodeJson.map(json => OtherLayout(json)),
    (l: OtherLayout) => l.data.asJson
  )

sealed trait Layout

object Layout:
  // https://circe.github.io/circe/codecs/adt.html#adts-encoding-and-decoding
  given Decoder[Layout] = List[Decoder[Layout]](
    Decoder[LineLayout].widen,
    Decoder[ImageLayout].widen,
    Decoder[OtherLayout].widen
  ).reduceLeft(_ or _)
  given Encoder[Layout] =
    case ll @ LineLayout(_, _, _)     => ll.asJson
    case il @ ImageLayout(_, _, _, _) => il.asJson
    case OtherLayout(data)            => data

sealed trait PropertyValue

object PropertyValue:
  given Codec[PropertyValue] = Codec.from(
    Decoder.decodeString.map(Literal.apply).or(Decoder.decodeJson.map(Custom.apply)),
    {
      case Literal(value) => value.asJson
      case Custom(value)  => value
    }
  )

  case class Literal(value: String) extends PropertyValue
  case class Custom(value: Json) extends PropertyValue

case class CirclePaint(`circle-radius`: Int, `circle-color`: String) extends BasePaint
  derives Codec.AsObject

case class LinePaint(
  `line-color`: PropertyValue,
  `line-width`: Int,
  `line-opacity`: Double,
  `line-gap-width`: Double = 0,
  `line-dasharray`: Option[List[Int]] = None
) extends BasePaint derives Codec.AsObject

object LinePaint:
  val blackColor = PropertyValue.Literal("#000")
  val darkGray = "#A9A9A9"
  def thin(color: PropertyValue = blackColor) = LinePaint(color, 1, 1)
  def dashed(color: PropertyValue = blackColor) =
    LinePaint(color, 1, 1, `line-dasharray` = Option(List(2, 4)))

// Bailout
case class OtherPaint(data: Json) extends BasePaint

case class FillPaint(`fill-color`: String, `fill-opacity`: Option[Double] = None) extends BasePaint
  derives Codec.AsObject

object OtherPaint:
  given Codec[OtherPaint] = Codec.from(
    Decoder.decodeJson.map(json => OtherPaint(json)),
    Encoder.encodeJson.contramap(t => t.data)
  )

sealed trait BasePaint

object BasePaint:
  given Decoder[BasePaint] =
    List[Decoder[BasePaint]](
      Decoder[LinePaint].widen,
      Decoder[CirclePaint].widen,
      Decoder[OtherPaint].widen,
      Decoder[FillPaint].widen
    ).reduceLeft(_ or _)
  given Encoder[BasePaint] =
    case lp @ LinePaint(_, _, _, _, _) => lp.asJson
    case cp @ CirclePaint(_, _)        => cp.asJson
    case fp @ FillPaint(_, _)          => fp.asJson
    case OtherPaint(data)              => data

/** @see
  *   https://www.mapbox.com/mapbox-gl-js/style-spec/#layer-type
  */
enum LayerType(val name: String):
  case Background extends LayerType("background")
  case Fill extends LayerType("fill")
  case Line extends LayerType("line")
  case Symbol extends LayerType("symbol")
  case Raster extends LayerType("raster")
  case Circle extends LayerType("circle")
  case FillExtrusion extends LayerType("fill-extrusion")
  case HeatMap extends LayerType("heatmap")
  case HillShade extends LayerType("hillshade")

object LayerType extends ValidatingCompanion[String, LayerType]:
  val all = Seq(Background, Fill, Line, Symbol, Raster, Circle, FillExtrusion, HeatMap, HillShade)

  override def build(input: String): Either[ErrorMessage, LayerType] =
    all
      .find(_.name.toLowerCase == input.toLowerCase)
      .toRight(ErrorMessage(s"Unknown layer type: '$input'."))

  override def write(t: LayerType): String = t.name

sealed trait LayerSource

object LayerSource:
  val reader: Decoder[LayerSource] =
    List[Decoder[LayerSource]](Decoder[StringLayerSource].widen, Decoder[InlineLayerSource].widen)
      .reduceLeft(_ or _)
  val writer: Encoder[LayerSource] =
    case sls @ StringLayerSource(_)    => sls.asJson
    case ils @ InlineLayerSource(_, _) => ils.asJson
  given Codec[LayerSource] = Codec.from[LayerSource](reader, writer)

/** @see
  *   https://docs.mapbox.com/style-spec/reference/layers/
  */
case class Layer(
  id: String,
  `type`: LayerType,
  source: LayerSource,
  layout: Option[Layout],
  paint: Option[BasePaint],
  minzoom: Option[Double] = None,
  maxzoom: Option[Double] = None
) derives Codec.AsObject

object Layer:
  def line(
    id: String,
    data: FeatureCollection,
    paint: BasePaint = LinePaint.thin(),
    minzoom: Option[Double] = None
  ): Layer =
    Layer(
      id,
      LayerType.Line,
      InlineLayerSource(data),
      Option(LineLayout.round),
      Option(paint),
      minzoom
    )

  def lineSource(
    id: String,
    source: String,
    paint: BasePaint = LinePaint.thin(),
    minzoom: Option[Double] = None
  ): Layer =
    Layer(
      id,
      LayerType.Line,
      StringLayerSource.unsafe(source),
      Option(LineLayout.round),
      Option(paint),
      minzoom
    )

  def fill(
    id: String,
    source: String,
    paint: BasePaint,
    minzoom: Option[Double] = None
  ): Layer =
    Layer(
      id,
      LayerType.Fill,
      StringLayerSource.unsafe(source),
      None,
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

case class StringLayerSource private (source: String) extends WrappedString with LayerSource:
  override def value = source

object StringLayerSource extends StringCompanion[StringLayerSource]:
  override def build(input: String): Either[ErrorMessage, StringLayerSource] =
    Right(StringLayerSource(input))

case class InlineLayerSource(`type`: String, data: FeatureCollection) extends LayerSource

object InlineLayerSource:
  // Recursive data structure:
  // FeatureCollection -> Feature -> Layer -> LayerSource -> FeatureCollection
  given Codec[InlineLayerSource] = deriveCodec[InlineLayerSource]

  def apply(data: FeatureCollection): InlineLayerSource =
    InlineLayerSource("geojson", data)

case class LayerFeature(
  `type`: String,
  geometry: Geometry,
  properties: Map[String, Json],
  layer: Option[Layer]
) derives Codec.AsObject:
  def addCoords(coords: Seq[Coord]): LayerFeature = copy(
    geometry = geometry.updateCoords(coords)
  )

  def props: Json = properties.asJson

object LayerFeature:
  val Key = "Feature"

  def point[W: Encoder](coord: Coord, props: W): LayerFeature =
    LayerFeature(PointGeometry(coord), props.asJson.asObject.map(_.toMap).getOrElse(Map.empty))
  def point(coord: Coord): LayerFeature = LayerFeature(PointGeometry(coord), Map.empty)
  def line(coords: Seq[Coord]): LayerFeature =
    LayerFeature(LineGeometry(coords), Map.empty)
  def apply(geometry: Geometry, properties: Map[String, Json]): LayerFeature =
    LayerFeature(Key, geometry, properties, None)

case class LayerFeatureCollection(`type`: String, features: Seq[LayerFeature])
  derives Codec.AsObject:
  def addCoords(coords: Seq[Coord]): LayerFeatureCollection = copy(
    features = features.map(_.addCoords(coords))
  )

object LayerFeatureCollection:
  val Key = "FeatureCollection"

  def apply(fs: Seq[LayerFeature]): LayerFeatureCollection = LayerFeatureCollection(Key, fs)

sealed trait Outcome

object Outcome:
  case object Added extends Outcome
  case object Updated extends Outcome
  case object Noop extends Outcome
