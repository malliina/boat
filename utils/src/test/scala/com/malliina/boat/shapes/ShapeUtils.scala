package com.malliina.boat.shapes

import munit.FunSuite

import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import org.geotools.api.data.DataStoreFinder
import org.geotools.data.shapefile.dbf.{DbaseFileReader, DbaseFileWriter}
import org.geotools.data.simple.SimpleFeatureCollection
import org.geotools.feature.DefaultFeatureCollection
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.geotools.geojson.feature.FeatureJSON
import org.geotools.geometry.jts.JTS
import org.geotools.referencing.CRS
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.locationtech.jts.geom.Geometry

import scala.jdk.CollectionConverters.{CollectionHasAsScala, MapHasAsJava, IteratorHasAsScala}

class ShapeUtils extends FunSuite:
  val userHome = Paths.get(sys.props("user.home"))

  test("write shapefile to geojson with geographic WGS84".ignore) {
    val file = userHome.resolve(".boat/Pysakointipaikat_aluePolygon.shp")
    val fileOut = userHome.resolve(".boat/Pysakointipaikat_alue.json")
    val store = DataStoreFinder.getDataStore(Map("url" -> file.toUri.toString).asJava)
    val exists = Files.exists(file)
    println(
      s"Exists $exists at $file stores ${DataStoreFinder.getAvailableDataStores.asScala.toList}"
    )
    //    store.getFeatureWriterAppend()
    val writer = new FeatureJSON()
    val collections: List[SimpleFeatureCollection] =
      store.getTypeNames.map(store.getFeatureSource).map(_.getFeatures).toList
    val transformedCollections = collections.map: coll =>
      val outCollection = new DefaultFeatureCollection()
      val srcCrs = coll.getSchema.getCoordinateReferenceSystem
      val targetCrs = DefaultGeographicCRS.WGS84
      val transformation = CRS.findMathTransform(srcCrs, targetCrs, true)
      val srcFeatures = coll.features()
      while srcFeatures.hasNext do
        val srcFeature = srcFeatures.next()
        val geo: Geometry = srcFeature.getDefaultGeometry.asInstanceOf[Geometry]
        val transformed = JTS.transform(geo, transformation)
        val builder = new SimpleFeatureBuilder(coll.getSchema)
        val feature = builder.buildFeature(null)
        feature.setAttributes(srcFeature.getAttributes)
        feature.setDefaultGeometry(transformed)
        outCollection.add(feature)
      srcFeatures.close()
      outCollection
    writer.writeFeatureCollection(transformedCollections.head, fileOut.toFile)
  }

  test("read shape file".ignore):
    val file = userHome.resolve(".boat/vaylat/vaylat.shp")
//    val fileOut = userHome.resolve(".boat/vaylat/vaylat.json")
    val store = DataStoreFinder.getDataStore(Map("url" -> file.toUri.toString).asJava)
//    println(store)
//    val writer = new FeatureJSON()
    val collections = store.getTypeNames.map(store.getFeatureSource).map(_.getFeatures).toList
    collections.map: coll =>
      val srcCrs = coll.getSchema.getCoordinateReferenceSystem
      println(srcCrs.getName)
      val targetCrs = DefaultGeographicCRS.WGS84
      val transform = CRS.findMathTransform(srcCrs, targetCrs, true)
      val geo: Geometry = coll.features().next().getDefaultGeometry.asInstanceOf[Geometry]
      val transformed = JTS.transform(geo, transform)
      println(transformed.toString)
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

  test("read shp file".ignore):
    val inFile: Path = ???
    val store = DataStoreFinder.getDataStore(Map("url" -> inFile.toUri.toString).asJava)
    val sources = store.getTypeNames.map(store.getFeatureSource).map(_.getFeatures.features())
    sources.foreach: src =>
      while src.hasNext do
        val feature = src.next()
        feature.getProperties.asScala
          .filter(prop => Option(prop.getValue).exists(_.toString.contains("LU-rannalla")))
          .foreach: prop =>
            println(s"${prop.getName} = ${prop.getValue}")

  test("convert dbf file from ISO-8859-1 to UTF-8".ignore):
    changeEncoding(
      userHome.resolve(".boat/vaylat/vaylat.dbf"),
      userHome.resolve(".boat/vaylat/vaylat-utf8.dbf")
    )

  test("convert limit file".ignore):
    changeEncoding(
      userHome.resolve(".boat/dbfs/rajoitusalue_a.dbf"),
      userHome.resolve(".boat/dbfs/rajoitusalue_a-utf8.dbf")
    )

  /** Fixes scandics in Finnish shapefiles.
    *
    * Converts dbf files from ISO-8859-1 to UTF-8.
    *
    * @param in
    *   in file
    * @param out
    *   out file
    * @param from
    *   in encoding
    * @param to
    *   out encoding
    */
  def changeEncoding(
    in: Path,
    out: Path,
    from: Charset = StandardCharsets.ISO_8859_1,
    to: Charset = StandardCharsets.UTF_8
  ) =
    Files.createFile(out)
    val inChannel = new FileInputStream(in.toFile).getChannel
    val reader = new DbaseFileReader(inChannel, true, from)

    val outChannel = FileChannel.open(out, StandardOpenOption.WRITE)
    val writer = new DbaseFileWriter(reader.getHeader, outChannel, to)

    while reader.hasNext do
      val entry = reader.readEntry()
      writer.write(entry)
    reader.close()
    writer.close()
