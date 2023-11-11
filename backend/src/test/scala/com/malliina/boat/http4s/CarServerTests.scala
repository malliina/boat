package com.malliina.boat.http4s

import cats.effect.IO
import com.malliina.boat.db.NewUser
import com.malliina.boat.{BoatNames, DeviceId, Errors, Latitude, LocationUpdate, LocationUpdates, Longitude, SimpleSourceMeta, SingleError, SourceType, UserToken, wh}
import com.malliina.http.{FullUrl, OkHttpResponse}
import com.malliina.measure.*
import com.malliina.values.{IdToken, Username, degrees}
import io.circe.syntax.EncoderOps
import org.http4s.Status
import org.http4s.Status.{NotFound, Ok, Unauthorized}
import org.http4s.headers.Authorization
import tests.{MUnitSuite, ServerSuite, TestEmailAuth, TestHttp}

import java.time.{OffsetDateTime, ZoneOffset}

class CarServerTests extends MUnitSuite with ServerSuite:
  val testCarId = DeviceId(1)
  val loc = LocationUpdate(
    Longitude.unsafe(24),
    Latitude.unsafe(60),
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
    longitude = Longitude.unsafe(24.2),
    latitude = Latitude.unsafe(60.3),
    speed = Option(80.kmh),
    date = loc.date.plusSeconds(10)
  )

  def postCarsUrl = baseUrl.append(Reverse.postCars.renderString)
  def getCarsUrl = baseUrl.append(Reverse.historyCars.renderString)

  test("POST call with no creds"):
    http
      .postJson(postCarsUrl, LocationUpdates(Nil, testCarId).asJson, Map.empty)
      .map: res =>
        assertEquals(res.status, Unauthorized.code)

  test("POST car locations with outdated jwt returns 401 with token expired"):
    postCarLocation(LocationUpdates(Nil, testCarId), token = TestEmailAuth.expiredToken).map: res =>
      assertEquals(res.status, Unauthorized.code)
      assert(
        res.parse[Errors].toOption.exists(_.errors.exists(_.key == SingleError.TokenExpiredKey))
      )

  test("POST call with working jwt"):
    val user = Username("test@example.com")
    val service = server().server.app
    val meta = SimpleSourceMeta(user, BoatNames.random(), SourceType.Vehicle)
    for
      _ <- service.userMgmt.deleteUser(user)
      _ <- service.userMgmt.addUser(
        NewUser(user, Option(TestEmailAuth[IO].testEmail), UserToken.random(), enabled = true)
      )
      car <- service.inserts.joinAsSource(meta)
      res <- postCarLocation(LocationUpdates(Nil, car.track.device))
    yield assertEquals(res.status, Ok.code)

  test("POST car locations for non-owned car fails"):
    postCarLocation(LocationUpdates(List(loc), DeviceId(123))).map: res =>
      assertEquals(res.status, NotFound.code)

  private def postCarLocation(
    updates: LocationUpdates,
    token: IdToken = TestEmailAuth.testToken,
    url: FullUrl = postCarsUrl
  ): IO[OkHttpResponse] =
    http.postJson(url, updates.asJson, headers(token))

  private def headers(token: IdToken) = Map(
    Authorization.name.toString -> s"Bearer $token",
    "Accept" -> "application/json"
  )
  def baseUrl = server().baseHttpUrl
  def http = TestHttp.client
