package com.malliina.boat.client

import akka.Done
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.malliina.boat.Constants.BoatTokenHeader
import com.malliina.boat.client.server.BoatConf
import com.malliina.boat.client.server.Device.GpsDevice
import com.malliina.http.FullUrl

import scala.concurrent.Future

object DeviceAgent {
  val Host = FullUrl("wss", "api.boat-tracker.com", "")
//  val Host = FullUrl("ws", "localhost:9000", "")
  val BoatUrl = Host / "/ws/boats"
  val DeviceUrl = Host / "/ws/devices"

  def apply(
    conf: BoatConf,
    url: FullUrl
  )(implicit as: ActorSystem, mat: Materializer): DeviceAgent =
    new DeviceAgent(conf, url)
}

/** Connects a TCP source to a WebSocket.
  *
  * @param conf agent conf
  */
class DeviceAgent(conf: BoatConf, url: FullUrl)(implicit as: ActorSystem, mat: Materializer) {
  val isGps = conf.device == GpsDevice
  val delimiter = TcpSource.crlf
  val tcp = TcpSource(conf.host, conf.port, delimiter)
  val ws = WebSocketClient(url, conf.token.map { t =>
    KeyValue(BoatTokenHeader, t.token)
  }.toList, as, mat)
  val toTcp =
    if (isGps)
      Source.single(TcpSource.watchMessage).concat(Source.maybe[ByteString])
    else
      Source.maybe[ByteString]

  /** Opens a TCP connection to the plotter and a WebSocket to the server. Reconnects on failures.
    *
    * @return a Future that completes when the connection has closed and no more reconnects are attempted
    */
  def connect(): Future[Done] = {
    tcp.connect(toTcp)
    ws.connect(tcp.sentencesHub)
  }

  def close(): Unit = {
    tcp.close()
    ws.close()
  }
}
