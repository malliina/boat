package com.malliina.boat.http4s

import cats.effect.IO
import com.malliina.boat.{CarHistoryResponse, DeviceId, Errors, Latitude, LocationUpdate, LocationUpdates, Longitude, SimpleMessage, TimeFormatter, wh}
import com.malliina.http.io.HttpClientIO
import com.malliina.http.{FullUrl, HttpClient, OkClient}
import com.malliina.measure.*
import com.malliina.values.{IdToken, UserId, degrees}
import io.circe.Decoder
import io.circe.syntax.EncoderOps
import okhttp3.{Interceptor, OkHttpClient, Protocol}
import org.http4s.Status.{NotFound, Ok, Unauthorized}
import org.http4s.headers.Authorization
import org.http4s.{Request, Status}
import tests.{MUnitSuite, ServerSuite, TestEmailAuth}

import java.time.{OffsetDateTime, ZoneOffset}
import java.util

class ServerTests extends MUnitSuite with ServerSuite:
  def meUrl = baseUrl.append(Reverse.me.renderString)

  test("can call server") {
    assertIO(status("/health"), Ok.code)
  }

  test("call with no creds") {
    client.get(baseUrl / "my-track").map { res =>
      assertEquals(res.status, Unauthorized.code)
      val errors = res.parse[Errors].toOption.get
      assertEquals(errors.message, Auth.noCredentials)
    }
  }

  test("GET profile with outdated jwt returns 401 with token expired") {
    client.get(meUrl, headers(TestEmailAuth.expiredToken)).map { res =>
      assertEquals(res.status, Unauthorized.code)
      assert(res.parse[Errors].toOption.exists(_.errors.exists(_.key == "token_expired")))
    }
  }

  test("apple app association") {
    assertIO(status(".well-known/apple-app-site-association"), Ok.code)
    assertIO(status(".well-known/assetlinks.json"), Ok.code)
  }

  private def headers(token: IdToken = TestEmailAuth.testToken) = Map(
    Authorization.name.toString -> s"Bearer $token",
    "Accept" -> "application/json"
  )

  private def status(path: String) =
    val url = baseUrl / path
    client.get(url).map(r => r.code)

  def baseUrl = server().baseHttpUrl
  def client = server().client
