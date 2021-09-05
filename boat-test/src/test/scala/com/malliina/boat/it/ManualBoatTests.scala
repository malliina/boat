package com.malliina.boat.it

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import com.malliina.boat.{BoatNames, BoatToken, BoatTokens, RawSentence, SentencesMessage}
import com.malliina.http.FullUrl
import com.malliina.util.FileUtils

import scala.jdk.CollectionConverters.CollectionHasAsScala

class ManualBoatTests extends BoatTests {
  //  val testFile = FileUtilities.userHome.resolve(".boat/Log2.txt")
  //  val testFile = FileUtilities.userHome.resolve(".boat/nmea0183-standard.log")
  val testFile = FileUtils.userHome.resolve(".boat/Log.txt")

  def sentences =
    Files.readAllLines(testFile, StandardCharsets.UTF_8).asScala.map(RawSentence.apply)

  // def relevantSentences = sentences.drop(10000).filter(s => s.sentence.startsWith("$GPGGA") || s.sentence.startsWith("$GPZDA"))
  def relevantSentences = sentences

//  def url = FullUrl.ws("localhost:9000", reverse.boats().toString)
  def url = FullUrl.wss("www.boat-tracker.com", reverse.ws.boats.renderString)

  http.test("local GPS reporting".ignore) { httpClient =>
    //    println("Lines " + gpsSentences.toList.length)
    val testMessages = relevantSentences.toList.grouped(1000).map(SentencesMessage.apply).toList
    openRandomBoat(url, httpClient) { boat =>
      testMessages.zipWithIndex.map {
        case (msg, idx) =>
          println(s"Sending batch $idx/${testMessages.length}...")
          boat.send(msg)
          Thread.sleep(5000)
      }
    }
  }

  http.test("slow anon GPS reporting".ignore) { httpClient =>
    val testMessages =
      relevantSentences.toList.grouped(30).map(SentencesMessage.apply).slice(50, 100).toList
    openRandomBoat(url, httpClient) { boat =>
      testMessages.foreach { msg =>
        boat.send(msg)
        Thread.sleep(500)
      }
    }
  }

  http.test("slow GPS reporting".ignore) { httpClient =>
    val token = BoatToken("todo")
    val testMessages =
      relevantSentences.toList.grouped(30).map(SentencesMessage.apply).slice(50, 100).toList
    openBoat(url, Right(token), httpClient) { boat =>
      testMessages.foreach { msg =>
        boat.send(msg)
        Thread.sleep(500)
      }
    }
  }

  test("generate names".ignore) {
    val name = BoatNames.random()
    val token = BoatTokens.random()
    println(name)
    println(token)
  }

  http.test("boat can connect".ignore) { httpClient =>
    val token = BoatToken("todo")
    openBoat(url, Right(token), httpClient) { boat =>
      Thread.sleep(10000)
    }
  }
}
