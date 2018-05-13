package com.malliina.boat.it

import com.malliina.boat.{BoatNames, CoordsEvent, RawSentence, SentencesEvent}
import play.api.libs.json.JsValue

import scala.concurrent.Promise

class SentenceTests extends BoatTests {
  test("sent sentence is received by viewer") {
    withBoat(BoatNames.random()) { boat =>
      val sentencePromise = Promise[SentencesEvent]()
      val coordPromise = Promise[CoordsEvent]()
      val testMessage = SentencesEvent(Seq(RawSentence("test")))

      def inspect(json: JsValue): Unit = {
        json.validate[SentencesEvent].foreach { ss => sentencePromise.trySuccess(ss) }
        json.validate[CoordsEvent].foreach { c => coordPromise.trySuccess(c) }
      }

      withViewer(inspect) { _ =>
        boat.sendMessage(testMessage)
        val received = await(sentencePromise.future)
        assert(received === testMessage)
        //        val coords = await(coordPromise.future).coords
        //        assert(coords === testCoord)
      }
    }
  }

  ignore("sent sentence is not received by unrelated viewer") {

  }
}
