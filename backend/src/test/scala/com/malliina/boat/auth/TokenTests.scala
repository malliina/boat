package com.malliina.boat.auth

import cats.effect.IO
import com.malliina.boat.LocalConf
import com.malliina.http.{FullUrl, OkHttpResponse}
import com.malliina.http.io.HttpClientIO
import com.malliina.http.HttpClient
import com.malliina.values.IdToken
import com.malliina.web.{AppleTokenValidator, ClientId, GoogleAuthFlow, SignInWithApple}
import tests.BaseSuite
import com.malliina.config.{ConfigOps, ConfigReadable}

import java.time.Instant
import scala.jdk.CollectionConverters.MapHasAsScala

class TokenTests extends BaseSuite:
  val boatConf = LocalConf.conf.getConfig("boat")

  http.test("google token validation".ignore) { httpClient =>
    val in = "token_here"
    val client = GoogleAuthFlow.keyClient(Seq(ClientId("client_id")), httpClient)
    client.validate(IdToken(in)).map { outcome =>
      assert(outcome.isRight)
      val v = outcome.toOption.get
      v.parsed.claims.getClaims.asScala.foreach { case (k, value) =>
        println(s"$k=$value")
      }
    }
  }

  http.test("validate iOS SIWA token".ignore) { client =>
    val token = IdToken("changeme")
    val v = AppleTokenValidator.app(client)
    v.validateToken(token, Instant.now()).map { outcome => }
  }

  http.test("validate siwa code".ignore) { client =>
    val fields = Map(
      "code" -> demoConf("code"),
      "grant_type" -> "authorization_code"
    )
    printRequest(fields, client)
  }

  http.test("validate refresh token".ignore) { client =>
    val fields = Map(
      "grant_type" -> "refresh_token",
      "refresh_token" -> demoConf("refresh")
    )
    printRequest(fields, client)
  }

  def demoConf(key: String) =
    val boatConf = LocalConf.localConf.getConfig("boat")
    val conf = boatConf.getConfig("demo")
    conf.getString(key)

  def printRequest(fields: Map[String, String], client: HttpClient[IO]): IO[Unit] =
    val siwaConf = boatConf
      .unsafe[SignInWithApple.Conf]("apple")
      .copy(clientId = AppleTokenValidator.boatClientId)
    val siwa = SignInWithApple(siwaConf)
    val privateKey = siwa.signInWithAppleToken(Instant.now())
    val conf = boatConf.getConfig("demo")
//    val code = conf.getString("code")
    client
      .postForm(
        FullUrl("https", "appleid.apple.com", "/auth/token"),
        Map(
          "client_id" -> siwaConf.clientId.value,
          "client_secret" -> privateKey.value
        ) ++ fields
      )
      .map(res => println(res.asString))
