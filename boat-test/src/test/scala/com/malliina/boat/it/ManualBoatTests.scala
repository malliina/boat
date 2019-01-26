package com.malliina.boat.it

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import com.malliina.boat.{BoatToken, RawSentence, SentencesMessage}
import com.malliina.http.FullUrl
import com.malliina.util.FileUtils

import scala.collection.JavaConverters.asScalaBufferConverter

class ManualBoatTests extends BoatTests {
  //  val testFile = FileUtilities.userHome.resolve(".boat/Log2.txt")
  //  val testFile = FileUtilities.userHome.resolve(".boat/nmea0183-standard.log")
  val testFile = FileUtils.userHome.resolve(".boat/Log.txt")

  def sentences = Files.readAllLines(testFile, StandardCharsets.UTF_8).asScala.map(RawSentence.apply)

  // def relevantSentences = sentences.drop(10000).filter(s => s.sentence.startsWith("$GPGGA") || s.sentence.startsWith("$GPZDA"))
  def relevantSentences = sentences

//  def url = FullUrl.ws("localhost:9000", reverse.boats().toString)

  def url = FullUrl.wss("www.boat-tracker.com", reverse.boatSocket().toString)

  ignore("local GPS reporting") {
    //    println("Lines " + gpsSentences.toList.length)
    val testMessages = relevantSentences.toList.grouped(1000).map(SentencesMessage.apply).toList
    openRandomBoat(url) { boat =>
      testMessages.zipWithIndex.map { case (msg, idx) =>
        println(s"Sending batch $idx/${testMessages.length}...")
        boat.send(msg)
        Thread.sleep(5000)
      }
    }
  }

  ignore("slow anon GPS reporting") {
    val testMessages = relevantSentences.toList.grouped(30).map(SentencesMessage.apply).slice(50, 100).toList
    openRandomBoat(url) { boat =>
      testMessages.foreach { msg =>
        boat.send(msg)
        Thread.sleep(500)
      }
    }
  }

  ignore("slow GPS reporting") {
    val token = BoatToken("todo")
    val testMessages = relevantSentences.toList.grouped(30).map(SentencesMessage.apply).slice(50, 100).toList
    openBoat(url, Right(token)) { boat =>
      testMessages.foreach { msg =>
        boat.send(msg)
        Thread.sleep(500)
      }
    }
  }

  ignore("boat can connect") {
    val token = BoatToken("todo")
    openBoat(url, Right(token)) { boat =>
      Thread.sleep(10000)
    }
  }
}
