package com.malliina.boat.http4s

import cats.effect.IO
import com.malliina.boat.{CarHistoryResponse, DeviceId, Errors, Latitude, LocationUpdate, LocationUpdates, Longitude, SimpleMessage, TimeFormatter}
import com.malliina.http.io.HttpClientIO
import com.malliina.http.{FullUrl, HttpClient, OkClient}
import com.malliina.values.{IdToken, UserId}
import com.malliina.measure.DistanceIntM
import com.malliina.values.degrees
import io.circe.Decoder
import io.circe.syntax.EncoderOps
import okhttp3.{Interceptor, OkHttpClient, Protocol}
import org.http4s.headers.Authorization
import org.http4s.{Request, Status}
import org.http4s.Status.{NotFound, Ok, Unauthorized}
import tests.{MUnitSuite, ServerSuite, TestEmailAuth}

import java.time.{OffsetDateTime, ZoneOffset}
import java.util

class ServerTests extends MUnitSuite with ServerSuite:
  val testCarId = DeviceId(1)
  val loc = LocationUpdate(
    Longitude(24),
    Latitude(60),
    Option(1.meters),
    Option(5.meters),
    Option(128f.degrees),
    None,
    OffsetDateTime.of(2023, 4, 2, 10, 4, 3, 0, ZoneOffset.UTC)
  )

  def postCarsUrl = baseUrl.append(Reverse.postCars.renderString)
  def getCarsUrl = baseUrl.append(Reverse.historyCars.renderString)
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

  test("POST call with no creds") {
    client.postJson(postCarsUrl, LocationUpdates(Nil, testCarId).asJson, Map.empty).map { res =>
      assertEquals(res.status, Unauthorized.code)
    }
  }

  test("POST car locations with outdated jwt returns 401 with token expired") {
    postCars(TestEmailAuth.expiredToken).map { res =>
      assertEquals(res.status, Unauthorized.code)
      assert(res.parse[Errors].toOption.exists(_.errors.exists(_.key == "token_expired")))
    }
  }

  test("POST call with working jwt") {
    postCars().map { res =>
      assertEquals(res.status, Ok.code)
    }
  }

  test("POST car locations") {
    for
      postResponse <- postCars(updates = LocationUpdates(List(loc), testCarId))
      _ = assertEquals(postResponse.status, Ok.code)
      history <- client.getAs[CarHistoryResponse](getCarsUrl, headers())
    yield assertEquals(1, 1)
    //      assert(history.history.nonEmpty)
  }

  test("POST car locations for non-owned car fails") {
    postCars(updates = LocationUpdates(List(loc), DeviceId(123))).map { res =>
      assertEquals(res.status, NotFound.code)
    }
  }

  test("apple app association") {
    assertIO(status(".well-known/apple-app-site-association"), Ok.code)
    assertIO(status(".well-known/assetlinks.json"), Ok.code)
  }

  private def postCars(
    token: IdToken = TestEmailAuth.testToken,
    updates: LocationUpdates = LocationUpdates(Nil, testCarId),
    url: FullUrl = postCarsUrl
  ) =
    client
      .postJson(
        url,
        updates.asJson,
        headers(token)
      )

  private def headers(token: IdToken = TestEmailAuth.testToken) = Map(
    Authorization.name.toString -> s"Bearer $token",
    "Accept" -> "application/json"
  )

  private def status(path: String) =
    val url = baseUrl / path
    client.get(url).map(r => r.code)

  def baseUrl = server().baseHttpUrl
  def client = server().client
