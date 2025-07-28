package com.malliina.boat

import cats.data.NonEmptyList
import com.malliina.geojson.{GeoLineString, GeoPoint}
import com.malliina.mapbox.{LngLat, LngLatBounds, MapboxMap, SimplePaddingOptions}
import com.malliina.turf.nearestPointOnLine
import com.malliina.values.ErrorMessage
import io.circe.syntax.EncoderOps
import io.circe.{Encoder, Json}

class GeoUtils(map: MapboxMap, val log: BaseLogger):
  val boatIconId = "boat-resized-opt-30"
  val carIconId = "car4"
  val trophyIconId = "trophy-gold-path"
  val deviceIconId = "device-icon"

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

  def fitTo(coords: Seq[Coord]): Unit =
    coords.headOption.foreach: head =>
      val init = LngLatBounds(head)
      val bs: LngLatBounds = coords
        .drop(1)
        .foldLeft(init): (bounds, c) =>
          bounds.extend(LngLat(c))
      try map.fitBounds(bs, SimplePaddingOptions(60))
      catch
        case e: Exception =>
          val sw = bs.getSouthWest()
          val nw = bs.getNorthWest()
          val ne = bs.getNorthEast()
          val se = bs.getSouthEast()
          log.error(s"Unable to fit using $sw $nw $ne $se", e)

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

  def oneGeoFeature(coords: Seq[MeasuredCoord]) =
    Feature(LineGeometry(coords.map(_.coord)), Map.empty)

  def lineFor(coords: Seq[Coord]): FeatureCollection =
    collectionFor(LineGeometry(coords), Map.empty)

  def boatSymbolLayer(id: String, coord: Coord) =
    Layer.symbol(id, pointFor(coord), ImageLayout(boatIconId, `icon-size` = 0.7))

  def pointFor(coord: Coord, props: Map[String, Json] = Map.empty) =
    collectionFor(PointGeometry(coord), props)

  def pointForProps[T: Encoder](coord: Coord, props: T) =
    pointFor(coord, props.asJson.asObject.map(_.toMap).getOrElse(Map.empty))

  private def collectionFor(geo: Geometry, props: Map[String, Json]): FeatureCollection =
    FeatureCollection(Seq(Feature(geo, props)))

  // https://www.movable-type.co.uk/scripts/latlong.html
  def bearing(from: Coord, to: Coord): Double =
    val dLon = to.lng.lng - from.lng.lng
    val y = Math.sin(dLon) * Math.cos(to.lat.lat)
    val x = Math.cos(from.lat.lat) * Math.sin(to.lat.lat) - Math.sin(from.lat.lat) * Math.cos(
      to.lat.lat
    ) * Math.cos(dLon)
    val brng = toDeg(Math.atan2(y, x))
    360 - ((brng + 360) % 360)

  private def toDeg(rad: Double) = rad * 180 / Math.PI

  def nearest[T](fromCoord: Coord, on: NonEmptyList[T])(
    c: T => Coord
  ): Either[ErrorMessage, NearestResult[T]] =
    val coords = on.map(c)
    val all = GeoLineString(coords.toList)
    log.info(s"Searching nearest update among ${coords.size} coords...")
    val turfPoint = GeoPoint(fromCoord)
    val nearestResult = nearestPointOnLine(all, turfPoint)
    val idx = nearestResult.properties.index
    if on.length > idx then Right(NearestResult(on.toList(idx), nearestResult.properties.dist))
    else Left(ErrorMessage(s"No trail at $fromCoord."))
