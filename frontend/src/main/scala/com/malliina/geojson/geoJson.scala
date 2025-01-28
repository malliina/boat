package com.malliina.geojson

import com.malliina.boat.{Coord, Feature, LineGeometry, PointGeometry}
import com.malliina.geojson.GeoType.*

import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.annotation.JSName

@js.native
trait GeoType extends js.Object:
  @JSName("type")
  def `type`: String = js.native

object GeoType:
  val Coordinates = "coordinates"
  val GeometryKey = "geometry"
  val Type = "type"
  val Properties = "properties"

@js.native
trait GeoFeature[G <: GeoGeometry] extends GeoType:
  def geometry: G
  def properties: js.Object = js.native

@js.native
trait GeoGeometry extends GeoType

@js.native
trait GeoLineString extends GeoFeature[GeoLineGeometry]:
  def geometry: GeoLineGeometry = js.native

object GeoLineString:
  def apply(coords: Seq[Coord]): GeoLineString =
    apply(GeoLineGeometry(coords))

  def apply(geometry: GeoLineGeometry): GeoLineString =
    literal(Type -> Feature.Key, Properties -> js.Object(), GeometryKey -> geometry)
      .asInstanceOf[GeoLineString]

@js.native
trait GeoLineGeometry extends GeoGeometry:
  def coordinates: js.Array[js.Array[Double]] = js.native

object GeoLineGeometry:
  def apply(coordinates: Seq[Coord]): GeoLineGeometry =
    literal(Type -> LineGeometry.Key, Coordinates -> coordinates.map(_.toArray.toJSArray).toJSArray)
      .asInstanceOf[GeoLineGeometry]

@js.native
trait GeoPoint extends GeoFeature[GeoPointGeometry]:
  def geometry: GeoPointGeometry = js.native

object GeoPoint:
  def apply(coord: Coord): GeoPoint =
    apply(GeoPointGeometry(coord))

  def apply(geometry: GeoPointGeometry): GeoPoint =
    literal(Type -> Feature.Key, Properties -> js.Object(), GeometryKey -> geometry)
      .asInstanceOf[GeoPoint]

@js.native
trait GeoPointGeometry extends GeoGeometry:
  def coordinates: js.Array[Double] = js.native

object GeoPointGeometry:
  def apply(coord: Coord): GeoPointGeometry =
    literal(Type -> PointGeometry.Key, Coordinates -> coord.toArray.toJSArray)
      .asInstanceOf[GeoPointGeometry]
