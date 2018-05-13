package com.malliina.boat.it

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import com.malliina.boat.{Coord, CoordsEvent, RawSentence, SentencesEvent}
import com.malliina.file.FileUtilities
import play.api.libs.json.JsValue

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.Promise

class BoatFileTests extends BoatTests {
  //  val testFile = FileUtilities.userHome.resolve(".boats/Log2.txt")
  val testFile = FileUtilities.userHome.resolve(".boats/nmea0183-standard.log")

  def sentences = Files.readAllLines(testFile, StandardCharsets.UTF_8).asScala.map(RawSentence.apply)

  def gpsSentences = sentences.filter(_.sentence.startsWith("$GPGGA"))

  val testTrack = Seq(
    "$GPGGA,140618,6009.1920,N,02453.5026,E,1,12,0.70,0,M,19.6,M,,*68",
    "$GPGGA,140819,6009.2206,N,02453.5233,E,1,12,0.60,-1,M,19.6,M,,*40",
    "$GPGGA,141209,6009.3630,N,02453.7997,E,1,12,0.60,-3,M,19.6,M,,*4F"
  ).map(RawSentence.apply)

  test("GPS reporting") {
    withBoat { boat =>
      val sentencePromise = Promise[SentencesEvent]()
      val coordPromise = Promise[CoordsEvent]()
      val testMessage = SentencesEvent(testTrack.take(1))
      val testCoord = Coord(24.89171, 60.1532)

      def inspect(json: JsValue): Unit = {
        json.validate[SentencesEvent].foreach { s => sentencePromise.trySuccess(s) }
        json.validate[CoordsEvent].foreach { c => coordPromise.trySuccess(c) }
      }

      withViewer(inspect) { _ =>
        boat.sendMessage(testMessage)
        val received = await(sentencePromise.future)
        assert(received === testMessage)
        val coord = await(coordPromise.future).coords
        assert(coord === Seq(testCoord))
      }
    }
  }

  //  ignore("local GPS reporting") {
  //    val testMessages = gpsSentences.toList.grouped(1000).map(SentencesEvent.apply).toList
  //    val url = FullUrl.ws("localhost:9000", reverse.boats().toString)
  //    withSocket(url, _ => ()) { boat =>
  //      testMessages.foreach { msg =>
  //        println(s"Sending $testMessages...")
  //        boat.sendMessage(msg)
  //        println(s"Sent $testMessages")
  //      }
  //    }
  //  }
  //
  //  ignore("slow GPS reporting") {
  //    val testMessages = gpsSentences.toList.grouped(50).map(SentencesEvent.apply).toList
  //    val url = FullUrl.ws("localhost:9000", reverse.boats().toString)
  //    withSocket(url, _ => ()) { boat =>
  //      testMessages.foreach { msg =>
  //        boat.sendMessage(msg)
  //        Thread.sleep(1000)
  //      }
  //    }
  //  }
}
