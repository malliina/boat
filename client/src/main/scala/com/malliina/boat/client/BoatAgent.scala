package com.malliina.boat.client

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.{Done, NotUsed}
import com.malliina.boat.Constants.BoatTokenHeader
import com.malliina.boat.client.server.BoatConf
import com.malliina.http.FullUrl

import scala.concurrent.Future

object BoatAgent {
  def prod(conf: BoatConf)(implicit as: ActorSystem, mat: Materializer): BoatAgent =
    apply(conf, WebSocketClient.ProdUrl)

  def apply(conf: BoatConf, url: FullUrl)(implicit as: ActorSystem, mat: Materializer): BoatAgent =
    new BoatAgent(conf, url)
}

/** Connects a TCP source to a WebSocket.
  *
  * @param conf agent conf
  */
class BoatAgent(conf: BoatConf, serverUrl: FullUrl)(implicit as: ActorSystem, mat: Materializer) {
  val tcp = TcpSource(conf.host, conf.port)
  val ws = WebSocketClient(serverUrl, conf.token.map { t => KeyValue(BoatTokenHeader, t.token) }.toList, as, mat)

  /** Opens a TCP connection to the plotter and a WebSocket to the server. Reconnects on failures.
    *
    * @return a Future that completes when the connection has closed and no more reconnects are attempted
    */
  def connect(): Future[Done] = {
    tcp.connect()
    ws.connect(tcp.sentencesHub)
  }

  def connectDirect(): Future[Done] = {
    ws.connect(tcp.sentencesSource.mapMaterializedValue(_ => NotUsed))
  }

  def close(): Unit = {
    tcp.close()
    ws.close()
  }
}
