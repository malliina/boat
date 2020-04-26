package com.malliina.boat.auth

import com.malliina.http.OkClient
import com.malliina.play.auth.KeyClient
import com.malliina.values.IdToken
import tests.BaseSuite

import scala.jdk.CollectionConverters.MapHasAsScala

class TokenTests extends BaseSuite {
  test("google token validation".ignore) {
    val in = "token_here"
    val client = KeyClient.google(Seq("client_id"), OkClient.default)
    val outcome = await(client.validate(IdToken(in)))
    assert(outcome.isRight)
    val v = outcome.toOption.get
    v.parsed.claims.getClaims.asScala.foreach {
      case (k, value) =>
        println(s"$k=$value")
    }
  }
}
