package com.malliina.boat.client

import akka.Done
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.malliina.http.FullUrl

import scala.concurrent.Future

object BoatAgent {
  def prod(host: String, port: Int, as: ActorSystem, mat: Materializer): BoatAgent =
    new BoatAgent(host, port, FullUrl.wss("boat.malliina.com", "/ws/boats"))(as, mat)
}

/**
  * @param host   plotter IP
  * @param port   plotter TCP port
  * @param server boat server WebSocket URL
  */
class BoatAgent(host: String, port: Int, server: FullUrl)(implicit as: ActorSystem, mat: Materializer) {
  val tcp = new TcpSource(host, port)
  val ws = new WebSocketClient(server)

  /** Opens a TCP connection to the plotter and a WebSocket to the server. Reconnects on failures.
    *
    * @return a Future that completes when the connection has closed and no more reconnects are attempted
    */
  def connect(): Future[Done] = ws.connect(tcp.sentencesSource)

  def close(): Unit = ws.close()
}
