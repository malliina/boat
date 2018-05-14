package com.malliina.boat.it

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import com.malliina.boat.{BoatNames, RawSentence, SentencesEvent}
import com.malliina.file.FileUtilities
import com.malliina.http.FullUrl

import scala.collection.JavaConverters.asScalaBufferConverter

class ManualBoatTests extends BoatTests {
  //  val testFile = FileUtilities.userHome.resolve(".boats/Log2.txt")
  val testFile = FileUtilities.userHome.resolve(".boats/nmea0183-standard.log")

  def sentences = Files.readAllLines(testFile, StandardCharsets.UTF_8).asScala.map(RawSentence.apply)

  def gpsSentences = sentences.filter(_.sentence.startsWith("$GPGGA"))

  def url = FullUrl.ws("localhost:9000", reverse.boats().toString)

  val track1 = Seq(
    "$GPGGA,150900,6006.3599,N,02504.0468,E,1,12,0.50,-3,M,19.5,M,,*4B",
    "$GPGGA,153813,6007.1158,N,02453.7227,E,1,12,0.50,-3,M,19.5,M,,*48",
    "$GPGGA,150900,6006.3599,N,02504.0468,E,1,12,0.50,-3,M,19.5,M,,*4B",
    "$GPGGA,154106,6008.0079,N,02452.0497,E,1,12,0.60,0,M,19.5,M,,*68",
    "$GPGGA,154817,6009.8242,N,02450.8647,E,1,12,0.60,-2,M,19.5,M,,*48"
  )

  val track2 = Seq(
    "$GPGGA,162112,6009.0969,N,02453.4521,E,1,12,0.70,6,M,19.5,M,,*6F",
    "$GPGGA,174239,6010.2076,N,02450.5518,E,1,12,0.50,0,M,19.5,M,,*63",
    "$GPGGA,124943,6009.5444,N,02448.4491,E,1,12,0.60,0,M,19.5,M,,*61",
    "$GPGGA,125642,6009.2559,N,02447.5942,E,1,12,0.60,1,M,19.5,M,,*68"
  )

  ignore("local GPS reporting") {
    val testMessages = gpsSentences.toList.grouped(1000).map(SentencesEvent.apply).toList
    withSocket(url, BoatNames.random(), _ => ()) { boat =>
      testMessages.foreach { msg =>
        println(s"Sending $testMessages...")
        boat.sendMessage(msg)
        println(s"Sent $testMessages")
      }
    }
  }

  ignore("slow GPS reporting") {
    val testMessages = gpsSentences.toList.grouped(50).map(SentencesEvent.apply).toList
    withSocket(url, BoatNames.random(), _ => ()) { boat =>
      testMessages.foreach { msg =>
        boat.sendMessage(msg)
        Thread.sleep(1000)
      }
    }
  }
}
