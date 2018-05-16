package com.malliina.boat.client

import com.malliina.boat.{RawSentence, SentencesEvent}
import com.malliina.http.FullUrl

object BoatClient {
  def apply(headers: Seq[KeyValue]) = {
    val url = FullUrl.wss("boat.malliina.com", "/ws/boats")
    val socket = new JsonSocket(url, CustomSSLSocketFactory.forHost("boat.malliina.com"), headers)
    new BoatClient(socket)
  }
}

class BoatClient(client: JsonSocket) {
  def onSentence(sentence: RawSentence) = {
    client.sendMessage(SentencesEvent(Seq(sentence)))
  }
}
