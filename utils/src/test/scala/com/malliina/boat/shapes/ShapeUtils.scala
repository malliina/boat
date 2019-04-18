package com.malliina.boat.shapes

import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}

import org.geotools.data.DataStoreFinder
import org.geotools.data.shapefile.dbf.{DbaseFileReader, DbaseFileWriter}
import org.geotools.data.simple.SimpleFeatureCollection
import org.geotools.feature.DefaultFeatureCollection
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.geotools.geojson.feature.FeatureJSON
import org.geotools.geometry.jts.JTS
import org.geotools.referencing.CRS
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.locationtech.jts.geom.Geometry
import org.scalatest.FunSuite

import scala.collection.JavaConverters._

class ShapeUtils extends FunSuite {
  ignore("write shapefile to geojson with geographic WGS84") {
    val file = Paths.get(sys.props("user.home")).resolve(".boat/vaylat/vaylat.shp")
    val fileOut = Paths.get(sys.props("user.home")).resolve(".boat/vaylat/vaylat-geo.json")
    val store = DataStoreFinder.getDataStore(Map("url" -> file.toUri.toString).asJava)
    //    println(store)
    //    store.getFeatureWriterAppend()
    val writer = new FeatureJSON()
    val collections: List[SimpleFeatureCollection] =
      store.getTypeNames.map(store.getFeatureSource).map(_.getFeatures).toList
    val transformedCollections = collections.map { coll =>
      val outCollection = new DefaultFeatureCollection()
      val srcCrs = coll.getSchema.getCoordinateReferenceSystem
      val targetCrs = DefaultGeographicCRS.WGS84
      val transformation = CRS.findMathTransform(srcCrs, targetCrs, true)
      val srcFeatures = coll.features()
      while (srcFeatures.hasNext) {
        val srcFeature = srcFeatures.next()
        val geo: Geometry = srcFeature.getDefaultGeometry.asInstanceOf[Geometry]
        val transformed = JTS.transform(geo, transformation)
        val builder = new SimpleFeatureBuilder(coll.getSchema)
        val feature = builder.buildFeature(null)
        feature.setAttributes(srcFeature.getAttributes)
        feature.setDefaultGeometry(transformed)
        outCollection.add(feature)
      }
      srcFeatures.close()
      outCollection
    }
    writer.writeFeatureCollection(transformedCollections.head, fileOut.toFile)
  }

  ignore("read shape file") {
    val file = Paths.get(sys.props("user.home")).resolve(".boat/vaylat/vaylat.shp")
    val fileOut = Paths.get(sys.props("user.home")).resolve(".boat/vaylat/vaylat.json")
    val store = DataStoreFinder.getDataStore(Map("url" -> file.toUri.toString).asJava)
//    println(store)
    val writer = new FeatureJSON()
    val collections = store.getTypeNames.map(store.getFeatureSource).map(_.getFeatures).toList
    collections.map { coll =>
      val srcCrs = coll.getSchema.getCoordinateReferenceSystem
      println(srcCrs.getName)
      val targetCrs = DefaultGeographicCRS.WGS84
      val transform = CRS.findMathTransform(srcCrs, targetCrs, true)
      val geo: Geometry = coll.features().next().getDefaultGeometry.asInstanceOf[Geometry]
      val transformed = JTS.transform(geo, transform)
      println(transformed.toString)
    }
    collections.head.getSchema.getCoordinateReferenceSystem
//    JTS.transform(collections.head)
    val ref = collections.last
      .features()
      .next()
      .getDefaultGeometryProperty
      .getType
      .getCoordinateReferenceSystem
    println(ref)
//    writer.writeFeatureCollection(collections.head, fileOut.toFile)
  }

  ignore("read shp file") {
    val inFile: Path = ???
    val store = DataStoreFinder.getDataStore(Map("url" -> inFile.toUri.toString).asJava)
    val sources = store.getTypeNames.map(store.getFeatureSource).map(_.getFeatures.features())
    sources.foreach { src =>
      while (src.hasNext) {
        val feature = src.next()
        feature.getProperties.asScala
          .filter(prop => Option(prop.getValue).exists(_.toString.contains("LU-rannalla")))
          .foreach { prop =>
            println(s"${prop.getName} = ${prop.getValue}")
          }
      }
    }
  }

  ignore("modify dbf file") {
    val inFile: Path = Paths.get("")
    val outFile: Path = Paths.get("")
    Files.createFile(outFile)
    val inChannel = new FileInputStream(inFile.toFile).getChannel
    val reader = new DbaseFileReader(inChannel, true, StandardCharsets.ISO_8859_1)

    val out = FileChannel.open(outFile, StandardOpenOption.WRITE)
    //    val out = new FileInputStream(outFile.toFile).getChannel
    val writer = new DbaseFileWriter(reader.getHeader, out, StandardCharsets.UTF_8)

    while (reader.hasNext) {
      val entry = reader.readEntry()
      writer.write(entry)
    }
    reader.close()
    writer.close()
  }
}
