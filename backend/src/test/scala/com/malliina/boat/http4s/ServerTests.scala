package com.malliina.boat.http4s

import com.malliina.boat.{ErrorConstants, MUnitSuite, ServerSuite, TestEmailAuth, TestHttp}
import com.malliina.http.Errors
import com.malliina.values.IdToken
import org.http4s.Status.{Ok, Unauthorized}
import org.http4s.headers.Authorization

class ServerTests extends MUnitSuite with ServerSuite:
  def meUrl = baseUrl.append(Reverse.me.renderString)

  test("can call server"):
    assertIO(status("/health"), Ok.code)

  test("call with no creds"):
    http
      .get(baseUrl / "my-track")
      .map: res =>
        assertEquals(res.status, Unauthorized.code)
        val errors = res.parse[Errors].toOption.get
        assertEquals(errors.message, Auth.noCredentials)

  test("GET profile with outdated jwt returns 401 with token expired"):
    http
      .get(meUrl, headers(TestEmailAuth.expiredToken))
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
      _ <- assertIO(status(".well-known/apple-app-site-association"), Ok.code)
      _ <- assertIO(status(".well-known/assetlinks.json"), Ok.code)
    yield 42

  private def headers(token: IdToken = TestEmailAuth.testToken) = Map(
    Authorization.name.toString -> s"Bearer $token",
    "Accept" -> "application/json"
  )

  private def status(path: String) =
    val url = baseUrl / path
    http.get(url).map(r => r.code)

  def baseUrl = server().baseHttpUrl
  def http = TestHttp.client
