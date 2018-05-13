package com.malliina.boat.client

import com.malliina.boat.{RawSentence, SentencesEvent}

class BoatClient(client: JsonSocket) {
  def onSentence(sentence: RawSentence) = {
    client.sendMessage(SentencesEvent(Seq(sentence)))
  }
}
