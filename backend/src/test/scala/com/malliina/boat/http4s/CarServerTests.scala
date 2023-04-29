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

// tests are fine, but CI fails, so ignored until CI is resolved
class CarServerTests extends MUnitSuite with ServerSuite:
  val testCarId = DeviceId(1)
  val loc = LocationUpdate(
    Longitude(24),
    Latitude(60),
    Option(1.meters),
    Option(5.meters),
    Option(128f.degrees),
    None,
    Option(110.kmh),
    Option(43000.5.wh),
    Option(80000.0.wh),
    Option(120.km),
    Option(24.5.celsius),
    Option(true),
    OffsetDateTime.of(2023, 4, 2, 10, 4, 3, 0, ZoneOffset.UTC)
  )
  val loc2 = loc.copy(
    longitude = Longitude(24.2),
    latitude = Latitude(60.3),
    speed = Option(80.kmh),
    date = loc.date.plusSeconds(10)
  )

  def postCarsUrl = baseUrl.append(Reverse.postCars.renderString)
  def getCarsUrl = baseUrl.append(Reverse.historyCars.renderString)

  // No reason to ignore other than unresolved CI failures
  test("POST call with no creds".ignore) {
    client.postJson(postCarsUrl, LocationUpdates(Nil, testCarId).asJson, Map.empty).map { res =>
      assertEquals(res.status, Unauthorized.code)
    }
  }

  test("POST car locations with outdated jwt returns 401 with token expired".ignore) {
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

  test("POST car locations".ignore) {
    for
      postResponse <- postCars(updates = LocationUpdates(List(loc, loc2), testCarId))
      _ = assertEquals(postResponse.status, Ok.code)
      history <- client.getAs[CarHistoryResponse](getCarsUrl, headers())
    yield
      val drives = history.history
      assert(drives.nonEmpty)
      val expected = loc.outsideTemperature.map(_.celsius.toInt)
      val latestDrive = drives.lastOption.toList
        .flatMap(_.updates)
      val hasTemp = latestDrive
        .exists(u => u.outsideTemperature.map(_.celsius.toInt) == expected)
      assert(hasTemp)
      // Distance between the two test coordinates is around 35 km
      assert(latestDrive.exists(_.diff > 30.km))
  }

  test("POST car locations for non-owned car fails".ignore) {
    postCars(updates = LocationUpdates(List(loc), DeviceId(123))).map { res =>
      assertEquals(res.status, NotFound.code)
    }
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
  def baseUrl = server().baseHttpUrl
  def client = server().client
