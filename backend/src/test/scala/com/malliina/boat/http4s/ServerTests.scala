package com.malliina.boat.http4s

import cats.effect.IO
import com.malliina.boat.{DeviceId, Errors, Latitude, LocationUpdate, LocationUpdates, Longitude, SimpleMessage}
import com.malliina.values.{IdToken, UserId}
import com.malliina.measure.DistanceIntM
import com.malliina.values.degrees
import io.circe.syntax.EncoderOps
import org.http4s.headers.Authorization
import org.http4s.{Request, Status}
import tests.{MUnitSuite, ServerSuite, TestEmailAuth}

import java.time.OffsetDateTime

class ServerTests extends MUnitSuite with ServerSuite:
  val testCarId = DeviceId(1)
  val loc = LocationUpdate(
    Longitude(24),
    Latitude(60),
    Option(1.meters),
    Option(5.meters),
    Option(128f.degrees),
    None,
    OffsetDateTime.now()
  )

  def carsUrl = baseUrl.append(Reverse.postCars.renderString)

  test("can call server") {
    assertIO(status("/health"), Status.Ok.code)
  }

  test("call with no creds") {
    client.get(baseUrl / "my-track").map { res =>
      assertEquals(res.status, Status.Unauthorized.code)
      val errors = res.parse[Errors].toOption.get
      assertEquals(errors.message, Auth.noCredentials)
    }
  }

  test("POST call with no creds") {
    client.postJson(carsUrl, LocationUpdates(Nil, testCarId).asJson, Map.empty).map { res =>
      assertEquals(res.status, Status.Unauthorized.code)
    }
  }

  test("POST call with bogus jwt") {
    postCars(IdToken("j.w.t")).map { res =>
      assertEquals(res.status, Status.Unauthorized.code)
    }
  }

  test("POST call with working jwt") {
    postCars(TestEmailAuth.testToken).map { res =>
      assertEquals(res.status, Status.Ok.code)
    }
  }

  test("POST car locations") {
    postCars(TestEmailAuth.testToken, LocationUpdates(List(loc), testCarId)).map { res =>
      assertEquals(res.status, Status.Ok.code)
    }
  }

  test("POST car locations for non-owned car fails") {
    postCars(TestEmailAuth.testToken, LocationUpdates(List(loc), DeviceId(123))).map { res =>
      assertEquals(res.status, Status.NotFound.code)
    }
  }

  test("apple app association") {
    assertIO(status(".well-known/apple-app-site-association"), Status.Ok.code)
    assertIO(status(".well-known/assetlinks.json"), Status.Ok.code)
  }

  private def postCars(
    token: IdToken,
    updates: LocationUpdates = LocationUpdates(Nil, testCarId)
  ) =
    client
      .postJson(
        carsUrl,
        updates.asJson,
        Map(Authorization.name.toString -> s"Bearer $token")
      )

  private def status(path: String): IO[Int] =
    val url = baseUrl / path
    client.get(url).map(r => r.code)

  def baseUrl = server().baseHttpUrl
  def client = server().client
