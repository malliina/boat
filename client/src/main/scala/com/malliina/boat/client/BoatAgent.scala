package com.malliina.boat.client

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.{Done, NotUsed}
import com.malliina.boat.BoatToken
import com.malliina.boat.client.BoatAgent.Conf
import com.malliina.http.FullUrl

import scala.concurrent.Future

object BoatAgent {
  def apply(host: String, port: Int, url: FullUrl)(implicit as: ActorSystem, mat: Materializer): BoatAgent =
    new BoatAgent(Conf(host, port, url, None))

  def prod(host: String, port: Int, as: ActorSystem, mat: Materializer): BoatAgent =
    apply(host, port, FullUrl.wss("boat.malliina.com", "/ws/boats"))(as, mat)

  case class Conf(plotterIp: String, plotterPort: Int, serverUrl: FullUrl, token: Option[BoatToken])

}

/** Connects a TCP source to a WebSocket.
  *
  * @param conf agent conf
  */
class BoatAgent(conf: Conf)(implicit as: ActorSystem, mat: Materializer) {
  val tcp = TcpSource(conf.plotterIp, conf.plotterPort)
  val ws = WebSocketClient(conf.serverUrl, Nil, as, mat)

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
