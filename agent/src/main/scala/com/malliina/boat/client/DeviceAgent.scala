package com.malliina.boat.client

import cats.effect.kernel.{Temporal, Resource}
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.malliina.boat.Constants.BoatTokenHeader
import com.malliina.boat.client.server.BoatConf
import com.malliina.boat.client.server.Device.GpsDevice
import com.malliina.http.FullUrl
import com.malliina.http.io.WebSocketIO
import fs2.Stream
import okhttp3.OkHttpClient

object DeviceAgent:
  val Host = FullUrl("wss", "api.boat-tracker.com", "")
//  val Host = FullUrl("ws", "localhost:9000", "")
  val BoatUrl = Host / "/ws/boats"
  val DeviceUrl = Host / "/ws/devices"

  def apply(conf: BoatConf, url: FullUrl, http: OkHttpClient)(implicit
    t: Temporal[IO],
    rt: IORuntime
  ): Resource[IO, DeviceAgent] =
    val headers = conf.token.toList.map(t => BoatTokenHeader -> t.token).toMap
    val isGps = conf.device == GpsDevice
    for
      tcp <- TcpClient.resource(conf.host, conf.port, TcpClient.linefeed)
      ws <- Resource.eval(WebSocketIO(url, headers, http))
    yield new DeviceAgent(tcp, ws, isGps)

/** Connects a TCP source to a WebSocket.
  *
  * @param conf
  *   agent conf
  * @param url
  *   URL to boat-tracker websocket
  */
class DeviceAgent(tcp: TcpClient, ws: WebSocketIO, isGps: Boolean)(implicit
  t: Temporal[IO],
  rt: IORuntime
):
  val toServer: Stream[IO, Array[Byte]] =
    if isGps then Stream.emit(TcpClient.watchMessage) ++ Stream.empty
    else Stream.empty

  /** Opens a TCP connection to the plotter and a WebSocket to the server. Reconnects on failures.
    */
  def connect(): IO[Unit] =
    tcp.connect(toServer.flatMap(arr => Stream.emits(arr))).compile.drain.unsafeRunAndForget()
//    tcp.unsafeConnect(toServer.flatMap(arr => Stream.emits(arr)))
    ws.open()
    tcp.sentencesHub
      .evalMap(s => IO.pure(ws.send(s)))
      .compile
      .drain

//  def unsafeConnect(): Unit = connect().unsafeRunAndForget()

  def close(): Unit =
    tcp.close()
    ws.close()
