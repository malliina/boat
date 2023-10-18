package com.malliina.boat.db

import java.io.ByteArrayOutputStream

import com.malliina.boat.Coord
import com.vividsolutions.jts.geom.impl.CoordinateArraySequenceFactory
import com.vividsolutions.jts.geom.{Coordinate, Geometry, GeometryFactory, PrecisionModel}
import com.vividsolutions.jts.io.{ByteOrderValues, OutputStreamOutStream, WKBReader, WKBWriter}

/** @see
  *   https://github.com/gquintana/jooq-mysql-spatial/blob/master/src/main/java/net/gquintana/jooq/mysql/GeometryConverter.java
  */
object SpatialUtils:
  private val byteOrder = ByteOrderValues.LITTLE_ENDIAN
  private val outputDimension = 2
  // TODO What is SRID 4326?
  private val srid = 4326
  private val gf =
    new GeometryFactory(new PrecisionModel(), srid, CoordinateArraySequenceFactory.instance())

  def fromBytes[T](bytes: Array[Byte]): T =
    val (_, wkb) = bytes.splitAt(4)
    val reader = new WKBReader(gf)
    reader.read(wkb).asInstanceOf[T]

  def coordToBytes(coord: Coord) =
    val point = gf.createPoint(new Coordinate(coord.lng.lng, coord.lat.lat))
    geoToBytes(point)

  private def geoToBytes(geo: Geometry): Array[Byte] =
    val out = new ByteArrayOutputStream()
    val sridBytes = new Array[Byte](4)
    ByteOrderValues.putInt(geo.getSRID, sridBytes, byteOrder)
    out.write(sridBytes)
    val wkbWriter = new WKBWriter(outputDimension, byteOrder)
    wkbWriter.write(geo, new OutputStreamOutStream(out))
    out.toByteArray
