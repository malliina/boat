package com.malliina.boat.it

import akka.stream.scaladsl.Sink
import com.malliina.boat._
import play.api.libs.json.JsValue

import scala.concurrent.Promise

class StaticBoatTests extends BoatTests {
  val testTrack = Seq(
    "$GPGGA,140618,6009.1920,N,02453.5026,E,1,12,0.70,0,M,19.6,M,,*68",
    "$GPGGA,140819,6009.2206,N,02453.5233,E,1,12,0.60,-1,M,19.6,M,,*40",
    "$GPGGA,141209,6009.3630,N,02453.7997,E,1,12,0.60,-3,M,19.6,M,,*4F"
  ).map(RawSentence.apply)

  test("GPS reporting") {
    val boatName = BoatNames.random()
    openTestBoat(boatName) { boat =>
      val coordPromise = Promise[CoordsEvent]()
      val testMessage = SentencesMessage(testTrack.take(1))
      val testCoord = Coord(24.89171, 60.1532)

      val sink = Sink.foreach[JsValue] { json =>
        json.validate[CoordsEvent].filter(_.from.boatName == boatName).foreach { c => coordPromise.trySuccess(c) }
      }

      openViewerSocket(sink, None) { _ =>
        boat.send(testMessage)
        //        assert(received.from.boatName === boatName)
        val coordsEvent = await(coordPromise.future)
        assert(coordsEvent.from.boatName === boatName)
        assert(coordsEvent.coords === Seq(testCoord))
      }
    }
  }
}
