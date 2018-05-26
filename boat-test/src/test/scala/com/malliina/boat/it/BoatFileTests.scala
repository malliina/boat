package com.malliina.boat.it

import akka.stream.scaladsl.Sink
import com.malliina.boat.{BoatNames, Coord, CoordsEvent, RawSentence, SentencesEvent, SentencesMessage}
import play.api.libs.json.JsValue

import scala.concurrent.Promise

class BoatFileTests extends BoatTests {
  val testTrack = Seq(
    "$GPGGA,140618,6009.1920,N,02453.5026,E,1,12,0.70,0,M,19.6,M,,*68",
    "$GPGGA,140819,6009.2206,N,02453.5233,E,1,12,0.60,-1,M,19.6,M,,*40",
    "$GPGGA,141209,6009.3630,N,02453.7997,E,1,12,0.60,-3,M,19.6,M,,*4F"
  ).map(RawSentence.apply)

  test("GPS reporting") {
    val boatName = BoatNames.random()
    val boat = openTestBoat(boatName)
    val sentencePromise = Promise[SentencesEvent]()
    val coordPromise = Promise[CoordsEvent]()
    val testMessage = SentencesMessage(testTrack.take(1))
    val testCoord = Coord(24.89171, 60.1532)

    val sink = Sink.foreach[JsValue] { json =>
      json.validate[SentencesEvent].foreach { s => sentencePromise.trySuccess(s) }
      json.validate[CoordsEvent].foreach { c => coordPromise.trySuccess(c) }
    }

    val _ = openViewerSocket(sink, None)
    boat.send(testMessage)
    val received = await(sentencePromise.future)
    assert(received.sentences === testMessage.sentences)
    assert(received.from.boat === boatName)
    val coord = await(coordPromise.future).coords
    assert(coord === Seq(testCoord))
  }
}
