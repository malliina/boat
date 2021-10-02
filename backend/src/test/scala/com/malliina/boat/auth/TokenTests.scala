package com.malliina.boat.auth

import cats.effect.unsafe.implicits.global
import com.malliina.http.io.HttpClientIO
import com.malliina.values.IdToken
import com.malliina.web.{ClientId, GoogleAuthFlow}
import tests.BaseSuite

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
