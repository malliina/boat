package com.malliina.boat.auth

import com.malliina.boat.LocalConf
import com.malliina.http.{FullUrl, OkHttpResponse}
import com.malliina.http.io.HttpClientIO
import com.malliina.values.IdToken
import com.malliina.web.{AppleTokenValidator, ClientId, GoogleAuthFlow, SignInWithApple}
import tests.BaseSuite
import com.malliina.config.{ConfigOps, ConfigReadable}

import java.time.Instant
import scala.jdk.CollectionConverters.MapHasAsScala

class TokenTests extends BaseSuite:
  val boatConf = LocalConf.localConf.getConfig("boat")

  test("google token validation".ignore) {
    val in = "token_here"
    val client = GoogleAuthFlow.keyClient(Seq(ClientId("client_id")), HttpClientIO())
    val outcome = client.validate(IdToken(in)).unsafeRunSync()
    assert(outcome.isRight)
    val v = outcome.toOption.get
    v.parsed.claims.getClaims.asScala.foreach { case (k, value) =>
      println(s"$k=$value")
    }
  }

  test("validate iOS SIWA token".ignore) {
    val token = IdToken("changeme")
    val v = AppleTokenValidator.app(HttpClientIO())
    val res = v.validateToken(token, Instant.now()).unsafeRunSync()
  }

  test("validate siwa code".ignore) {
    val fields = Map(
      "code" -> demoConf("code"),
      "grant_type" -> "authorization_code"
    )
    printRequest(fields)
  }

  test("validate refresh token".ignore) {
    val fields = Map(
      "grant_type" -> "refresh_token",
      "refresh_token" -> demoConf("refresh")
    )
    printRequest(fields)
  }

  def demoConf(key: String) =
    val boatConf = LocalConf.localConf.getConfig("boat")
    val conf = boatConf.getConfig("demo")
    conf.getString(key)

  def printRequest(fields: Map[String, String]) =
    val siwaConf = boatConf
      .unsafe[SignInWithApple.Conf]("apple")
      .copy(clientId = AppleTokenValidator.boatClientId)
    val siwa = SignInWithApple(siwaConf)
    val privateKey = siwa.signInWithAppleToken(Instant.now())
    val conf = boatConf.getConfig("demo")
//    val code = conf.getString("code")
    val http = HttpClientIO()
    val res: OkHttpResponse = http
      .postForm(
        FullUrl("https", "appleid.apple.com", "/auth/token"),
        Map(
          "client_id" -> siwaConf.clientId.value,
          "client_secret" -> privateKey.value
        ) ++ fields
      )
      .unsafeRunSync()
    println(res.asString)
