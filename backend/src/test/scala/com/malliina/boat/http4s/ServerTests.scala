package com.malliina.boat.http4s

import cats.effect.IO
import com.malliina.boat.{Errors, LocationUpdates, SimpleMessage}
import io.circe.syntax.EncoderOps
import org.http4s.headers.Authorization
import org.http4s.{Request, Status}
import tests.{MUnitSuite, ServerSuite}

class ServerTests extends MUnitSuite with ServerSuite:
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
    client.postJson(baseUrl / "cars" / "locations", LocationUpdates(Nil).asJson, Map.empty).map {
      res =>
        assertEquals(res.status, Status.Unauthorized.code)
    }
  }

  test("POST call with bogus jwt") {
    client
      .postJson(
        baseUrl / "cars" / "locations",
        LocationUpdates(Nil).asJson,
        Map(Authorization.name.toString -> "j.w.t")
      )
      .map { res =>
        assertEquals(res.status, Status.Unauthorized.code)
      }
  }

  test("apple app association") {
    assertIO(status(".well-known/apple-app-site-association"), Status.Ok.code)
    assertIO(status(".well-known/assetlinks.json"), Status.Ok.code)
  }

  private def status(path: String): IO[Int] =
    val url = baseUrl / path
    client.get(url).map(r => r.code)

  def baseUrl = server().baseHttpUrl
  def client = server().client
