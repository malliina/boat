package com.malliina.boat

import com.malliina.mapbox.MapboxMap
import io.circe.*
import io.circe.syntax.EncoderOps

trait GeoUtils:
  val boatIconId = "boat-resized-opt-30"
  val carIconId = "car4"
  val trophyIconId = "trophy-gold-path"
  val deviceIconId = "device-icon"

  def map: MapboxMap

  def updateOrSet(layer: Layer): Outcome =
    map
      .findSource(layer.id)
      .map: geo =>
        layer.source match
          case InlineLayerSource(_, data) =>
            geo.updateData(data)
            Outcome.Updated
          case StringLayerSource(_) =>
            Outcome.Noop
      .getOrElse:
        map.putLayer(layer)
        Outcome.Added

  def drawLine(id: String, geoJson: FeatureCollection, paint: LinePaint = LinePaint.thin()) =
    updateOrSet(Layer.line(id, geoJson, paint, None))

  def lineForTrack(coords: Seq[MeasuredCoord]): FeatureCollection =
    FeatureCollection(speedFeatures(coords))

  def speedFeatures(coords: Seq[MeasuredCoord]): Seq[Feature] = coords.length match
    case 0 =>
      Nil
    case 1 =>
      val knots = coords.map(_.speed.toKnots).sum / coords.size
      val feature = Feature(
        LineGeometry(coords.map(_.coord)),
        Map(TimedCoord.SpeedKey -> knots.asJson)
      )
      Seq(feature)
    case _ =>
      coords
        .zip(coords.drop(1))
        .map: (start, end) =>
          val avgSpeed: Double = (start.speed + end.speed).toKnots / 2
          Feature(
            LineGeometry(Seq(start, end).map(_.coord)),
            Map(TimedCoord.SpeedKey -> avgSpeed.asJson)
          )

  def lineFor(coords: Seq[Coord]): FeatureCollection =
    collectionFor(LineGeometry(coords), Map.empty)

  def pointFor(coord: Coord, props: Map[String, Json] = Map.empty) =
    collectionFor(PointGeometry(coord), props)

  def pointForProps[T: Encoder](coord: Coord, props: T) =
    pointFor(coord, props.asJson.asObject.map(_.toMap).getOrElse(Map.empty))

  private def collectionFor(geo: Geometry, props: Map[String, Json]): FeatureCollection =
    FeatureCollection(Seq(Feature(geo, props)))
