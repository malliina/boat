package com.malliina.boat.it

import akka.stream.scaladsl.Sink
import com.malliina.boat._
import com.malliina.play.models.Password
import play.api.libs.json.JsValue

import scala.concurrent.{Await, Promise, TimeoutException}

class SentenceTests extends BoatTests {
  ignore("anonymously sent sentence is received by anonymous viewer") {
    val boat = openTestBoat(BoatNames.random())
    val sentencePromise = Promise[SentencesEvent]()
    val coordPromise = Promise[CoordsEvent]()
    val testMessage = SentencesMessage(Seq(RawSentence("$GPGGA,154106,6008.0079,N,02452.0497,E,1,12,0.60,0,M,19.5,M,,*68")))

    val in = Sink.foreach[JsValue] { json =>
      json.validate[SentencesEvent].foreach { ss => sentencePromise.trySuccess(ss) }
      json.validate[CoordsEvent].foreach { c => coordPromise.trySuccess(c) }
    }
    val _ = openViewerSocket(in, None)
    boat.send(testMessage)
    val received = await(sentencePromise.future)
    assert(received.sentences === testMessage.sentences)
    val coords = await(coordPromise.future).coords
    val expectedCoords = Seq(Coord(24.867495, 60.133465))
    assert(coords === expectedCoords)
    boat.close()
  }

  test("sent events are not received by unrelated viewer") {
    val testUser = User("User1")
    val testPass = Password("demo")
    await(components.users.addUser(testUser, testPass))
    val creds = Option(Creds(testUser, testPass))
    val boat = openTestBoat(BoatNames.random(), creds)
    val authSentencePromise = Promise[SentencesEvent]()
    val sentencePromise = Promise[SentencesEvent]()
    val coordPromise = Promise[CoordsEvent]()
    val testMessage = SentencesMessage(Seq(RawSentence("$GPGGA,154106,6008.0079,N,02452.0497,E,1,12,0.60,0,M,19.5,M,,*68")))
    val anonSink = Sink.foreach[JsValue] { json =>
      json.validate[SentencesEvent].foreach { se => sentencePromise.trySuccess(se) }
      json.validate[CoordsEvent].foreach { c => coordPromise.trySuccess(c) }
    }
    val authSink = Sink.foreach[JsValue] { json =>
      json.validate[SentencesEvent].foreach { se => authSentencePromise.trySuccess(se) }
    }
    val anonClient = openViewerSocket(anonSink, None)
    val authClient = openViewerSocket(authSink, creds)
    boat.send(testMessage)
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
