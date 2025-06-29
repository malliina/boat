package com.malliina.boat.http4s

import cats.effect.IO
import com.malliina.boat.db.NewUser
import com.malliina.boat.*
import com.malliina.http.{CSRFConf, Errors, FullUrl}
import com.malliina.measure.*
import com.malliina.values.{IdToken, Username, degrees, lat, lng}
import io.circe.syntax.EncoderOps
import org.http4s.Status.{NotFound, Ok, Unauthorized}
import org.http4s.Uri
import org.http4s.headers.{Accept, Authorization}

import java.time.{OffsetDateTime, ZoneOffset}

class CarServerTests extends MUnitSuite with ServerFunSuite:
  val testCarId = DeviceId(1)
  val loc = LocationUpdate(
    24.lng,
    60.lat,
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

  srv.test("POST call with no creds"): s =>
    http
      .postJson(
        s.baseHttpUrl / Reverse.postCars,
        LocationUpdates(Nil, testCarId).asJson,
        Map(s.csrf.headerName.toString -> s.csrf.noCheck)
      )
      .map: res =>
        assertEquals(res.status, Unauthorized.code)

  srv.test("POST car locations with outdated jwt returns 401 with token expired"): s =>
    http
      .postJson(
        s.baseHttpUrl / Reverse.postCars,
        LocationUpdates(Nil, testCarId).asJson,
        headersStr(TestEmailAuth.expiredToken, s.csrf)
      )
      .map: res =>
        assertEquals(res.status, Unauthorized.code)
        assert(
          res
            .parse[Errors]
            .toOption
            .exists(_.errors.exists(_.key == ErrorConstants.TokenExpiredKey))
        )

  srv.test("POST call with working jwt"): s =>
    successfulTest(s)(carId => LocationUpdates(Nil, carId))

  srv.test("POST call with working jwt and update with no speed"): s =>
    val json =
      """
        |{ "updates" : [ { "longitude" : -122.084, "latitude" : 37.421998333333335, "altitudeMeters" : 5.0, "accuracyMeters" : 5.0, "bearing" : null, "bearingAccuracyDegrees" : null, "speed" : null, "batteryLevel" : null, "batteryCapacity" : null, "rangeRemaining" : null, "outsideTemperature" : 25.0, "nightMode" : false, "date" : "2025-02-08T16:43:14.517+02:00" } ], "carId" : 1324 }""".stripMargin
    val ups = io.circe.parser.decode[LocationUpdates](json).fold(err => throw err, identity)
    successfulTest(s)(carId => ups.copy(carId = carId))

  srv.test("POST car locations for non-owned car fails"): s =>
    http
      .postJson(
        s.baseHttpUrl / Reverse.postCars,
        LocationUpdates(List(loc), DeviceId(123)).asJson,
        headersStr(TestEmailAuth.testToken, s.csrf)
      )
      .map: res =>
        assertEquals(res.status, NotFound.code)

  def successfulTest(s: ServerTools)(ups: DeviceId => LocationUpdates) =
    val user = Username("test@example.com")
    val service = s.server.app
    val meta = SimpleSourceMeta(user, BoatNames.random(), SourceType.Vehicle, Language.default)
    for
      _ <- service.userMgmt.deleteUser(user)
      _ <- service.userMgmt.addUser(
        NewUser(user, Option(TestEmailAuth[IO].testEmail), UserToken.random(), enabled = true)
      )
      car <- service.inserts.joinAsSource(meta)
      res <- http.postJson(
        s.baseHttpUrl / Reverse.postCars,
        ups(car.track.device).asJson,
        headersStr(TestEmailAuth.testToken, s.csrf)
      )
    yield assertEquals(res.status, Ok.code)

  private def headersStr(token: IdToken, csrf: CSRFConf) =
    headers(token, csrf).map((k, v) => k.toString -> v)
  private def headers(token: IdToken, csrf: CSRFConf) =
    Map(
      Authorization.name -> s"Bearer $token",
      Accept.headerInstance.name -> "application/json",
      csrf.headerName -> csrf.noCheck
    )

  def http = TestHttp.client

  extension (url: FullUrl) def /(uri: Uri) = url.append(uri.renderString)
