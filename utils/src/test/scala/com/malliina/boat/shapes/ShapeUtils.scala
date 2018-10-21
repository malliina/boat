package com.malliina.boat.shapes

import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.{Path, StandardOpenOption}

import org.geotools.data.DataStoreFinder
import org.geotools.data.shapefile.dbf.{DbaseFileReader, DbaseFileWriter}
import org.scalatest.FunSuite

import scala.collection.JavaConverters._

class ShapeUtils extends FunSuite {
  ignore("read shp file") {
    val inFile: Path = ???
    val store = DataStoreFinder.getDataStore(Map("url" -> inFile.toUri.toString).asJava)
    val sources = store.getTypeNames.map(store.getFeatureSource).map(_.getFeatures.features())
    sources.foreach { src =>
      while (src.hasNext) {
        val feature = src.next()
        feature.getProperties.asScala.filter(prop => Option(prop.getValue).exists(_.toString.contains("LU-rannalla"))).foreach { prop =>
          println(s"${prop.getName} = ${prop.getValue}")
        }
      }
    }
  }

  ignore("modify dbf file") {
    val inFile: Path = ???
    val outFile: Path = ???
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
