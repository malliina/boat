package com.malliina.boat.client

import cats.effect.unsafe.implicits.global
import cats.effect.IO
import com.malliina.boat.{Constants, RawSentence, SentencesMessage}
import com.malliina.http.FullUrl
import com.malliina.http.io.{HttpClientIO, WebSocketF}
import com.malliina.http.io.SocketEvent.{Open, TextMessage}

class WebSocketClientTests extends AsyncSuite:
  test("can connect to api.boat-tracker.com".ignore):
    val socketResource = for
      http <- HttpClientIO.resource[IO]
      socket <- WebSocketF.build[IO](DeviceAgent.BoatUrl, Map.empty, http.client)
    yield socket
    socketResource
      .use: socket =>
        socket.events
          .collect:
            case o @ Open(_, _) =>
              o
          .take(1)
          .compile
          .toList
          .unsafeRunSync()
          .take(1)
        IO.unit
      .unsafeRunSync()

  test("connect boat to boat-tracker.com".ignore):
    val url = FullUrl.ws("localhost:9000", "/ws/devices")
//    val url = FullUrl.wss("api.boat-tracker.com", "/ws/devices")
    val samples = Seq(
      "$GPZDA,150016.000,17,08,2019,,*51",
      "$GPRMC,150016.000,A,6009.1753,N,02453.2470,E,0.00,166.59,170819,,,A*68",
      "$GPGGA,150016.000,6009.1753,N,02453.2470,E,1,11,0.77,-13.9,M,19.6,M,,*79",
      "$GPGSA,A,3,12,01,03,14,31,18,17,32,11,19,23,,1.14,0.77,0.83*0C",
      "$GPGRS,150016.000,1,-24.0,23.5,18.8,28.5,8.71,5.88,21.1,15.8,8.88,15.5,18.7,*40",
      "$GPGST,150016.000,00020,008.0,006.2,145.0,007.4,006.8,00036*62",
      "$GPTXT,01,01,02,ANTSTATUS=OPEN*2B",
      "$GPGSV,4,1,13,22,78,221,14,01,64,183,22,03,58,257,34,14,51,074,35*71"
    ).map(RawSentence.apply)
    val msg = SentencesMessage(samples)
    val token = "todo"
    val socketResource = for
      http <- HttpClientIO.resource[IO]
      socket <- WebSocketF.build[IO](url, Map(Constants.BoatTokenHeader -> token), http.client)
    yield socket
    socketResource.use: (client: WebSocketF[IO]) =>
      val stream = client.events.evalMap:
        case Open(_, _)                   => client.send(msg)
        case TextMessage(socket, message) => IO(println(message))
        case _                            => IO.unit
      stream.compile.drain
