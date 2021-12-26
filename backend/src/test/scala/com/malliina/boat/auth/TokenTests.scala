package com.malliina.boat.auth

import com.malliina.http.io.HttpClientIO
import com.malliina.values.IdToken
import com.malliina.web.{ClientId, GoogleAuthFlow, AppleTokenValidator}
import tests.BaseSuite
import java.time.Instant
import scala.jdk.CollectionConverters.MapHasAsScala

class TokenTests extends BaseSuite:
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

  test("iOS SIWA token".ignore) {
    val token = IdToken("changeme")
    val v = AppleTokenValidator.app(HttpClientIO())
    val res = v.validateToken(token, Instant.now()).unsafeRunSync()
  }
