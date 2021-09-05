package com.malliina.boat.it

import com.malliina.boat._
import com.malliina.util.AppLogger

import scala.concurrent.Promise

class StaticBoatTests extends BoatTests {
  val log = AppLogger(getClass)

  val testTrack = Seq(
    "$SDDPT,23.9,0.0,*43",
    "$GPVTG,51.0,T,42.2,M,2.4,N,4.4,K,A*25",
    "$GPZDA,000008,09,07,2018,-03,00*6B",
    "$SDMTW,15.2,C*02",
    "$GPGGA,140618,6009.1920,N,02453.5026,E,1,12,0.70,0,M,19.6,M,,*68",
    "$GPZDA,141735,04,05,2018,-03,00*69",
    "$GPGGA,140819,6009.2206,N,02453.5233,E,1,12,0.60,-1,M,19.6,M,,*40",
    "$GPGGA,141209,6009.3630,N,02453.7997,E,1,12,0.60,-3,M,19.6,M,,*4F"
  ).map(RawSentence.apply)

  http.test("GPS reporting") { client =>
    val boatName = BoatNames.random()
    openTestBoat(boatName, client) { boat =>
      val coordPromise = Promise[CoordsEvent]()
      val testMessage = SentencesMessage(testTrack.take(6))
      val testCoord = Coord.buildOrFail(24.89171, 60.1532)

      openViewerSocket(client, None) { socket =>
        socket.jsonMessages.map { json =>
          json.as[CoordsEvent].toOption.filter(_.from.boatName == boatName).foreach { c =>
            coordPromise.trySuccess(c)
          }
        }.compile.drain.unsafeRunAsyncAndForget()
        val sent = boat.send(testMessage)
        assert(sent)
        val coordsEvent = await(coordPromise.future, 3.seconds)
        assertEquals(coordsEvent.from.boatName, boatName)
        assertEquals(coordsEvent.coords.map(_.coord), List(testCoord))
        val first = coordsEvent.coords.head
        assertEquals(first.boatTimeMillis, 1525443455000L)
      }
    }
  }
}
