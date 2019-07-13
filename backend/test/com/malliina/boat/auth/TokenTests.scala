package com.malliina.boat.auth

import com.malliina.http.OkClient
import com.malliina.play.auth.{IdToken, KeyClient}
import tests.BaseSuite

import scala.jdk.CollectionConverters.MapHasAsScala

class TokenTests extends BaseSuite {
  ignore("google token validation") {
    val in = "token_here"
    val client = KeyClient.google(Seq("client_id"), OkClient.default)
    val outcome = await(client.validate(IdToken(in)))
    assert(outcome.isRight)
    val v = outcome.toOption.get
    v.parsed.claims.getClaims.asScala.foreach { case (k, value) =>
      println(s"$k=$value")
    }
  }
}
