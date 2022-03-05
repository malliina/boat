package com.malliina.boat.client

import cats.effect.IO
import cats.effect.kernel.{Resource, Temporal}
import com.malliina.boat.Constants.BoatTokenHeader
import com.malliina.boat.client.DeviceAgent.log
import com.malliina.boat.client.server.BoatConf
import com.malliina.boat.client.server.Device.GpsDevice
import com.malliina.http.FullUrl
import com.malliina.http.io.{SocketEvent, WebSocketIO}
import com.malliina.util.AppLogger
import fs2.Stream
import okhttp3.OkHttpClient

object DeviceAgent:
  private val log = AppLogger(getClass)

  val Host: FullUrl = FullUrl("wss", "api.boat-tracker.com", "")
//  val Host = FullUrl("ws", "localhost:9000", "")
  val BoatUrl: FullUrl = Host / "/ws/boats"
  val DeviceUrl: FullUrl = Host / "/ws/devices"

  def fromConf(conf: BoatConf, url: FullUrl, http: OkHttpClient)(implicit
    t: Temporal[IO]
  ): Resource[IO, DeviceAgent] =
    val headers = conf.token.toList.map(t => BoatTokenHeader -> t.token).toMap
    val isGps = conf.device == GpsDevice
    for
      tcp <- Resource.eval(TcpClient.default(conf.host, conf.port, TcpClient.linefeed))
      ws <- WebSocketIO(url, headers, http)
    yield DeviceAgent(tcp, ws, isGps)

/** Connects a TCP source to a WebSocket.
  *
  * @param conf
  *   agent conf
  * @param url
  *   URL to boat-tracker websocket
  */
class DeviceAgent(tcp: TcpClient, ws: WebSocketIO, isGps: Boolean)(implicit t: Temporal[IO]):
  val toServer: Stream[IO, Array[Byte]] =
    if isGps then Stream.emit(TcpClient.watchMessage) ++ Stream.empty
    else Stream.empty

  /** Opens a TCP connection to the plotter and a WebSocket to the server. Reconnects on failures.
    */
  def connect: Stream[IO, Unit] =
    val tcpConnection: Stream[IO, Unit] = tcp.connect(toServer.flatMap(arr => Stream.emits(arr)))
    val webSocketConnection: Stream[IO, SocketEvent] = ws.events
    val connections: Stream[IO, Unit] =
      tcpConnection.concurrently(webSocketConnection.map(s => ()))
    val sendStream =
      tcp.sentencesHub.evalMap(s => IO(log.debug(s"Sending $s")) >> ws.send(s)).map(_ => ())
    connections.concurrently(sendStream).onFinalize(close)

  def close: IO[Unit] =
    IO(tcp.close()) >> ws.close
