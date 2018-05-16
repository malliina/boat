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
