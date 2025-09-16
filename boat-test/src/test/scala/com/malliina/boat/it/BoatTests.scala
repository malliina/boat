package com.malliina.boat.it

import cats.effect.IO
import com.malliina.boat.*
import com.malliina.boat.client.{HttpUtil, KeyValue}
import com.malliina.http.SocketEvent.Open
import com.malliina.http.{FullUrl, HttpClient, ReconnectingSocket, WebSocketOps}
import com.malliina.values.{Password, Username}
import org.http4s.Uri

type Socket = ReconnectingSocket[IO, ? <: WebSocketOps[IO]]

abstract class BoatTests extends BaseSuite with ServerSuite with BoatSockets:
  export concurrent.duration.DurationInt

  def openTestBoat[T](boat: BoatName, httpClient: HttpClient[IO])(
    code: Socket => IO[T]
  ): IO[T] =
    openBoat(urlFor(reverse.ws.boats), Left(boat), httpClient)(code)

  def openViewerSocket[T](httpClient: HttpClient[IO], creds: Option[Creds] = None)(
    code: Socket => IO[T]
  ): IO[T] =
    val headers = creds
      .map: c =>
        KeyValue(HttpUtil.Authorization, HttpUtil.authorizationValue(c.user, c.pass.pass))
      .toList
    openWebSocket(reverse.ws.updates, headers, httpClient)(code)

  private def openWebSocket[T](
    path: Uri,
    headers: List[KeyValue],
    httpClient: HttpClient[IO]
  )(code: Socket => IO[T]): IO[T] =
    openSocket(urlFor(path), headers, httpClient)(code)

  private def urlFor(call: Uri): FullUrl = server().baseWsUrl.append(call.renderString)

trait BoatSockets:
  self: BaseSuite =>
  def openRandomBoat[T](url: FullUrl, httpClient: HttpClient[IO])(
    code: Socket => IO[T]
  ): IO[T] =
    openBoat(url, Left(BoatNames.random()), httpClient)(code)

  def openBoat[T](url: FullUrl, boat: Either[BoatName, BoatToken], httpClient: HttpClient[IO])(
    code: Socket => IO[T]
  ): IO[T] =
    val headers = boat.fold(
      name => KeyValue.build(Constants.BoatNameHeader.toString, name),
      t => KeyValue.build(Constants.BoatTokenHeader.toString, t)
    )
    openSocket(url, List(headers), httpClient): socket =>
      code(socket)

  def openSocket[T](url: FullUrl, headers: List[KeyValue], httpClient: HttpClient[IO])(
    code: Socket => IO[T]
  ): IO[T] =
    httpClient
      .socket(url, headers.map(kv => kv.key -> kv.value).toMap)
      .use: socket =>
        val openEvents = socket.events.collect:
          case o @ Open(_) =>
            o
        openEvents.take(1).compile.toList >> code(socket)

case class Creds(user: Username, pass: Password)
