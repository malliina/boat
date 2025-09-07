package com.malliina.boat.http4s

import cats.effect.IO
import com.malliina.boat.{ErrorConstants, MUnitSuite, ServerSuite, TestEmailAuth, TestHttp}
import com.malliina.http.{Errors, HttpResponse}
import com.malliina.values.IdToken
import org.http4s.Status.{Ok, Unauthorized}
import org.http4s.Uri
import org.http4s.headers.Authorization
import org.http4s.implicits.uri

import scala.concurrent.duration.DurationInt

class ServerTests extends MUnitSuite with ServerSuite:
  test("can call server"):
    assertIO(status(uri"/health"), Ok.code)

  test("call with no creds"):
    get(uri"/my-track")
      .map: res =>
        assertEquals(res.status, Unauthorized.code)
        val errors = res.parse[Errors].toOption.get
        assertEquals(errors.message, Auth.noCredentials)

  test("GET profile with outdated jwt returns 401 with token expired"):
    get(Reverse.me, headers(TestEmailAuth.expiredToken))
      .map: res =>
        assertEquals(res.status, Unauthorized.code)
        assert(
          res
            .parse[Errors]
            .toOption
            .exists(_.errors.exists(_.key == ErrorConstants.TokenExpiredKey))
        )

  test("apple app association"):
    for
      _ <- assertIO(status(uri"/.well-known/apple-app-site-association"), Ok.code)
      _ <- assertIO(status(uri"/.well-known/assetlinks.json"), Ok.code)
    yield 42

  private def headers(token: IdToken) = Map(
    Authorization.name.toString -> s"Bearer $token",
    "Accept" -> "application/json"
  )

  private def status(uri: Uri) =
    get(uri).map(r => r.code)

  private def get(uri: Uri, headers: Map[String, String] = Map.empty): IO[HttpResponse] =
    val url = baseUrl.append(uri.renderString)
    val duration = 10.seconds
    http
      .get(url, headers)
      .timeoutTo(
        duration,
        IO.raiseError(Exception(s"Request to '$url' timed out after $duration."))
      )

  def baseUrl = server().baseHttpUrl
  def http = TestHttp.client
