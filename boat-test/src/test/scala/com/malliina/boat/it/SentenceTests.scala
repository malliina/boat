package com.malliina.boat.it

import com.malliina.boat._
import com.malliina.play.models.Password
import play.api.libs.json.JsValue

import scala.concurrent.{Await, Promise, TimeoutException}

class SentenceTests extends BoatTests {
  test("anonymously sent sentence is received by anonymous viewer") {
    withBoat(BoatNames.random()) { boat =>
      val sentencePromise = Promise[SentencesEvent]()
      val coordPromise = Promise[CoordsEvent]()
      val testMessage = SentencesMessage(Seq(RawSentence("$GPGGA,154106,6008.0079,N,02452.0497,E,1,12,0.60,0,M,19.5,M,,*68")))

      def inspect(json: JsValue): Unit = {
        json.validate[SentencesEvent].foreach { ss => sentencePromise.trySuccess(ss) }
        json.validate[CoordsEvent].foreach { c => coordPromise.trySuccess(c) }
      }

      withViewer(inspect) { _ =>
        boat.sendMessage(testMessage)
        val received = await(sentencePromise.future)
        assert(received.sentences === testMessage.sentences)
        //        val coords = await(coordPromise.future).coords
        //        assert(coords === testCoord)
      }
    }
  }

  test("sent events are not received by unrelated viewer") {
    val testUser = User("User1")
    val testPass = Password("demo")
    await(components.users.addUser(testUser, testPass))
    val creds = Option(Creds(testUser, testPass))
    withBoat(BoatNames.random(), creds = creds) { boat =>
      val authSentencePromise = Promise[SentencesEvent]()
      val sentencePromise = Promise[SentencesEvent]()
      val coordPromise = Promise[CoordsEvent]()
      val testMessage = SentencesMessage(Seq(RawSentence("$GPGGA,154106,6008.0079,N,02452.0497,E,1,12,0.60,0,M,19.5,M,,*68")))

      def handleAnonMessage(json: JsValue): Unit = {
        json.validate[SentencesEvent].foreach { se => sentencePromise.trySuccess(se) }
        json.validate[CoordsEvent].foreach { c => coordPromise.trySuccess(c) }
      }

      def handleAuthMessage(json: JsValue): Unit = {
        json.validate[SentencesEvent].foreach { se => authSentencePromise.trySuccess(se) }
      }

      withViewer(handleAuthMessage, creds) { _ =>
        withViewer(handleAnonMessage) { _ =>
          boat.sendMessage(testMessage)
          val received = await(authSentencePromise.future)
          assert(received.sentences === testMessage.sentences)
          intercept[TimeoutException] {
            Await.result(sentencePromise.future, 500.millis)
          }
          intercept[TimeoutException] {
            Await.result(coordPromise.future, 500.millis)
          }
        }
      }
    }
  }
}
