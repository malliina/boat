package com.malliina.boat.client

import cats.effect.{Async, Sync}
import cats.effect.kernel.Resource
import cats.syntax.all.catsSyntaxFlatMapOps
import com.malliina.boat.BoatToken
import com.malliina.boat.Constants.BoatTokenHeader
import com.malliina.boat.client.DeviceAgent.log
import com.malliina.boat.client.server.BoatConf
import com.malliina.boat.client.server.Device.GpsDevice
import com.malliina.http.FullUrl
import com.malliina.http.io.{SocketEvent, WebSocketF}
import com.malliina.util.AppLogger
import fs2.Stream
import fs2.concurrent.SignallingRef
import fs2.io.net.Network
import okhttp3.OkHttpClient
import cats.syntax.show.toShow

object DeviceAgent:
  private val log = AppLogger(getClass)

  val HttpsHost = FullUrl.https("api.boat-tracker.com", "")
  val Host: FullUrl = FullUrl.wss("api.boat-tracker.com", "")
//  val Host = FullUrl("ws", "localhost:9000", "")
  val BoatUrl: FullUrl = Host / "/ws/boats"
  val DeviceUrl: FullUrl = Host / "/ws/devices"

  def fromConf[F[_]: {Async, Network}](
    conf: BoatConf,
    url: FullUrl,
    http: OkHttpClient
  ): Resource[F, DeviceAgent[F]] =
    val headers = conf.token.toList.map(t => BoatTokenHeader.toString -> t.show).toMap
    val isGps = conf.device == GpsDevice
    for
      tcp <- Resource.eval(TCPClient.default(conf.host, conf.port))
      ws <- WebSocketF.build[F](url, headers, http)
      signal <- Resource.eval(SignallingRef[F, Boolean](false))
    yield DeviceAgent(tcp, ws, signal, isGps)

/** Connects a TCP source to a WebSocket.
  *
  * @param conf
  *   agent conf
  * @param url
  *   URL to boat-tracker websocket
  */
class DeviceAgent[F[_]: Async](
  tcp: TCPClient[F],
  ws: WebSocketF[F],
  signal: SignallingRef[F, Boolean],
  isGps: Boolean
):
  val toServer: Stream[F, Array[Byte]] =
    if isGps then Stream.emit(TCPClient.watchMessage) ++ Stream.empty
    else Stream.empty

  /** Opens a TCP connection to the plotter and a WebSocket to the server. Reconnects on failures.
    */
  def connect: Stream[F, Unit] =
    val tcpConnection: Stream[F, Unit] = tcp.connect(toServer.flatMap(arr => Stream.emits(arr)))
    val webSocketConnection: Stream[F, SocketEvent] = ws.events
    val sendStream =
      tcp.sentencesHub
        .evalMap(s => delay(log.debug(s"Sending $s")) >> ws.send(s))
    Stream.never
      .concurrently(tcpConnection)
      .concurrently(webSocketConnection)
      .concurrently(sendStream)
      .interruptWhen(signal)
      .onFinalize(close)

  def close: F[Unit] =
    delay(log.info(s"Closing agent to ${tcp.hostPort} and ${ws.url}.")) >>
      signal.getAndSet(true) >> tcp.close >> ws.close

  private def delay[T](thunk: => T) = Sync[F].delay(thunk)
