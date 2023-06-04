package com.malliina.boat.http4s

import cats.effect.IO
import com.malliina.boat.Errors
import com.malliina.http.HttpClient
import com.malliina.values.IdToken
import org.http4s.Status.{Ok, Unauthorized}
import org.http4s.headers.Authorization
import org.http4s.Status
import tests.{MUnitSuite, ServerSuite, TestEmailAuth}

class ServerTests extends MUnitSuite with ServerSuite:
  def meUrl = baseUrl.append(Reverse.me.renderString)

  client.test("can call server") { http =>
    assertIO(status(http, "/health"), Ok.code)
  }

  client.test("call with no creds") { http =>
    http.get(baseUrl / "my-track").map { res =>
      assertEquals(res.status, Unauthorized.code)
      val errors = res.parse[Errors].toOption.get
      assertEquals(errors.message, Auth.noCredentials)
    }
  }

  client.test("GET profile with outdated jwt returns 401 with token expired") { http =>
    http.get(meUrl, headers(TestEmailAuth.expiredToken)).map { res =>
      assertEquals(res.status, Unauthorized.code)
      assert(res.parse[Errors].toOption.exists(_.errors.exists(_.key == "token_expired")))
    }
  }

  client.test("apple app association".ignore) { http =>
    assertIO(status(http, ".well-known/apple-app-site-association"), Ok.code)
    assertIO(status(http, ".well-known/assetlinks.json"), Ok.code)
  }

  private def headers(token: IdToken = TestEmailAuth.testToken) = Map(
    Authorization.name.toString -> s"Bearer $token",
    "Accept" -> "application/json"
  )

  private def status(http: HttpClient[IO], path: String) =
    val url = baseUrl / path
    http.get(url).map(r => r.code)

  def baseUrl = server().baseHttpUrl
