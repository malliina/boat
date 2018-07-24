package com.malliina.boat.db

import com.vividsolutions.jts.geom.impl.CoordinateArraySequenceFactory
import com.vividsolutions.jts.geom.{Geometry, GeometryFactory, PrecisionModel}
import com.vividsolutions.jts.io._

object SpatialUtils {
  private val wktWriterHolder = new ThreadLocal[WKTWriter]
  private val wktReaderHolder = new ThreadLocal[WKTReader]
  private val wkbWriterHolder = new ThreadLocal[WKBWriter]
  private val wkb3DWriterHolder = new ThreadLocal[WKBWriter]
  private val wkbReaderHolder = new ThreadLocal[WKBReader]

  def toLiteral(geom: Geometry): String = {
    if (wktWriterHolder.get == null) wktWriterHolder.set(new WKTWriter())
    wktWriterHolder.get.write(geom)
  }

  def fromLiteral2[T](value: String): T = {
    if (wktReaderHolder.get == null) wktReaderHolder.set(new WKTReader())
    splitRSIDAndWKT(value) match {
      case (srid, wkt) => {
        val geom =
          if (wkt.startsWith("00") || wkt.startsWith("01"))
            fromBytes(WKBReader.hexToBytes(wkt))
          else wktReaderHolder.get.read(wkt)

        if (srid != -1) geom.setSRID(srid)
        geom.asInstanceOf[T]
      }
    }
  }

  def fromLiteral[T](value: String): T = {
    new WKTReader().read(value).asInstanceOf[T]
  }

  def toBytes(geom: Geometry): Array[Byte] = {
    if (geom != null && geom.getCoordinate != null && !(java.lang.Double.isNaN(geom.getCoordinate.z))) {
      if (wkb3DWriterHolder.get == null) wkb3DWriterHolder.set(new WKBWriter(3, true))
      wkb3DWriterHolder.get.write(geom)
    } else {
      if (wkbWriterHolder.get == null) wkbWriterHolder.set(new WKBWriter(2, true))
      wkbWriterHolder.get.write(geom)
    }
  }

  def fromBytes[T](bytes: Array[Byte]): T = {
    val (sridBytes, wkb) = bytes.splitAt(4)
    val srid = ByteOrderValues.getInt(sridBytes, ByteOrderValues.LITTLE_ENDIAN)
    val gff = new GeometryFactory(new PrecisionModel(), srid, CoordinateArraySequenceFactory.instance)
    val reader = new WKBReader(gff)
    reader.read(wkb).asInstanceOf[T]
  }

  def fromBytes2[T](bytes: Array[Byte]): T = {
    if (wkbReaderHolder.get == null) wkbReaderHolder.set(new WKBReader())
    wkbReaderHolder.get.read(bytes).asInstanceOf[T]
  }

  /** copy from [[org.postgis.PGgeometry#splitSRID]] */
  private def splitRSIDAndWKT(value: String): (Int, String) = {
    if (value.startsWith("SRID=")) {
      val index = value.indexOf(';', 5) // srid prefix length is 5
      if (index == -1) {
        throw new java.sql.SQLException("Error parsing Geometry - SRID not delimited with ';' ")
      } else {
        val srid = Integer.parseInt(value.substring(0, index))
        val wkt = value.substring(index + 1)
        (srid, wkt)
      }
    } else (-1, value)
  }
}