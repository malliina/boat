package com.malliina.boat.it

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import com.malliina.boat.{BoatNames, RawSentence, SentencesMessage}
import com.malliina.file.FileUtilities
import com.malliina.http.FullUrl

import scala.collection.JavaConverters.asScalaBufferConverter

class ManualBoatTests extends BoatTests {
  //  val testFile = FileUtilities.userHome.resolve(".boat/Log2.txt")
  val testFile = FileUtilities.userHome.resolve(".boat/nmea0183-standard.log")

  def sentences = Files.readAllLines(testFile, StandardCharsets.UTF_8).asScala.map(RawSentence.apply)

  def gpsSentences = sentences.filter(_.sentence.startsWith("$GPGGA"))

  //  def url = FullUrl.ws("localhost:9000", reverse.boats().toString)

  def url = FullUrl.wss("boat.malliina.com", reverse.boats().toString)

  ignore("local GPS reporting") {
    val testMessages = gpsSentences.toList.grouped(1000).map(SentencesMessage.apply).toList
    openBoat(url, BoatNames.random()) { boat =>
      testMessages.foreach { msg =>
        println(s"Sending $testMessages...")
        boat.send(msg)
        println(s"Sent $testMessages")
      }
    }
  }

  ignore("slow GPS reporting") {
    val testMessages = gpsSentences.toList.grouped(2).map(SentencesMessage.apply).slice(50, 100).toList
    openBoat(url, BoatNames.random()) { boat =>
      testMessages.foreach { msg =>
        boat.send(msg)
        Thread.sleep(500)
      }
    }
  }
}
