package com.malliina.boat.it

import com.malliina.boat.*
import com.malliina.boat.client.{HttpUtil, KeyValue}
import com.malliina.http.FullUrl
import com.malliina.http.io.HttpClientIO
import com.malliina.http.io.WebSocketIO
import com.malliina.http.io.SocketEvent.Open
import com.malliina.util.AppLogger
import com.malliina.values.{Password, Username}
import io.circe.Encoder
import org.http4s.Uri
import tests.{AsyncSuite, ServerSuite}

abstract class BoatTests extends AsyncSuite with ServerSuite with BoatSockets:
  def openTestBoat[T](boat: BoatName, httpClient: HttpClientIO)(code: TestBoat => T): T =
    openBoat(urlFor(reverse.ws.boats), Left(boat), httpClient)(code)

  def openViewerSocket[T](httpClient: HttpClientIO, creds: Option[Creds] = None)(
    code: WebSocketIO => T
  ): T =
    val headers = creds.map { c =>
      KeyValue(HttpUtil.Authorization, HttpUtil.authorizationValue(c.user, c.pass.pass))
    }.toList
    openWebSocket(reverse.ws.updates, headers, httpClient)(code)

  private def openWebSocket[T](
    path: Uri,
    headers: List[KeyValue],
    httpClient: HttpClientIO
  )(code: WebSocketIO => T): T =
    openSocket(urlFor(path), headers, httpClient)(code)

  private def urlFor(call: Uri): FullUrl = server().baseWsUrl.append(call.renderString)

object BoatSockets:
  private val log = AppLogger(getClass)

trait BoatSockets:
  self: AsyncSuite =>
  def openRandomBoat[T](url: FullUrl, httpClient: HttpClientIO)(code: TestBoat => T): T =
    openBoat(url, Left(BoatNames.random()), httpClient)(code)

  def openBoat[T](url: FullUrl, boat: Either[BoatName, BoatToken], httpClient: HttpClientIO)(
    code: TestBoat => T
  ): T =
    val headers = boat.fold(
      name => KeyValue(Constants.BoatNameHeader, name.name),
      t => KeyValue(Constants.BoatTokenHeader, t.token)
    )
    openSocket(url, List(headers), httpClient) { socket =>
      code(new TestBoat(socket))
    }

  def openSocket[T](url: FullUrl, headers: List[KeyValue], httpClient: HttpClientIO)(
    code: WebSocketIO => T
  ): T =
    val socket = WebSocketIO(url, headers.map(kv => kv.key -> kv.value).toMap, httpClient.client)
      .unsafeRunSync()
    try
      socket.allEvents.compile.drain.unsafeRunAndForget()
      val opens = socket.events.collect { case o @ Open(_, _) =>
        o
      }
      opens.take(1).compile.toList.unsafeRunSync()
      code(socket)
    finally socket.close()

  class TestBoat(val socket: WebSocketIO):
    def send[T: Encoder](t: T) = socket.send(t)
    def close(): Unit = socket.close()

case class Creds(user: Username, pass: Password)
