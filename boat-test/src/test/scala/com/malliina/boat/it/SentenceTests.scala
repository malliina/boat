package com.malliina.boat.it

import akka.stream.scaladsl.Sink
import com.malliina.boat._
import com.malliina.boat.db.NewUser
import com.malliina.values.{Password, Username}
import play.api.libs.json.JsValue

import scala.concurrent.{Await, Promise, TimeoutException}

class SentenceTests extends BoatTests {
  // TODO Why is this test ignored?
  ignore("anonymously sent sentence is received by anonymous viewer") {
    openTestBoat(BoatNames.random()) { boat =>
      val sentencePromise = Promise[SentencesEvent]()
      val coordPromise = Promise[CoordsEvent]()
      val testMessage = SentencesMessage(Seq(RawSentence("$GPGGA,154106,6008.0079,N,02452.0497,E,1,12,0.60,0,M,19.5,M,,*68")))

      val in = Sink.foreach[JsValue] { json =>
        json.validate[SentencesEvent].foreach { ss => sentencePromise.trySuccess(ss) }
        json.validate[CoordsEvent].foreach { c => coordPromise.trySuccess(c) }
      }
      openViewerSocket(in, None) { _ =>
        boat.send(testMessage)
        val received = await(sentencePromise.future)
        assert(received.sentences === testMessage.sentences)
        val coords = await(coordPromise.future).coords
        val expectedCoords = Seq(Coord(24.867495, 60.133465))
        assert(coords === expectedCoords)
      }
    }
  }

  // Ignored because the design is the opposite of the test
  ignore("sent events are not received by unrelated viewer") {
    val testUser = Username("User1")
    val testPass = Password("demo")
    await(components.users.addUser(NewUser(testUser, None, UserToken.random(), enabled = true)))
    val creds = Option(Creds(testUser, testPass))
    openTestBoat(BoatNames.random()) { boat =>
      val authPromise = Promise[CoordsEvent]()
      val anonPromise = Promise[CoordsEvent]()
      val testMessage = SentencesMessage(Seq(RawSentence("$GPGGA,154106,6008.0079,N,02452.0497,E,1,12,0.60,0,M,19.5,M,,*68")))
      val anonSink = Sink.foreach[JsValue] { json =>
        json.validate[CoordsEvent].filter(_.from.username == testUser).foreach { c => anonPromise.trySuccess(c) }
      }
      val authSink = Sink.foreach[JsValue] { json =>
        json.validate[CoordsEvent].filter(_.from.username == testUser).foreach { se => authPromise.trySuccess(se) }
      }
      openViewerSocket(anonSink, None) { _ =>
        openViewerSocket(authSink, creds) { _ =>
          boat.send(testMessage)
          await(authPromise.future)
          intercept[TimeoutException] {
            Await.result(anonPromise.future, 500.millis)
          }
        }
      }
    }

  }
}
