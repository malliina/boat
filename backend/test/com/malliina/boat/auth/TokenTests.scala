package com.malliina.boat.auth

import com.malliina.http.OkClient
import com.malliina.play.auth.{IdToken, KeyClient}
import tests.BaseSuite

import scala.collection.JavaConverters.mapAsScalaMapConverter

class TokenTests extends BaseSuite {
  ignore("google token validation") {
    val in = "token_here"
    val client = KeyClient.google(Seq("client_id"), OkClient.default)
    val outcome = await(client.validate(IdToken(in)))
    assert(outcome.isRight)
    val v = outcome.right.get
    v.parsed.claims.getClaims.asScala.foreach { case (k, value) =>
      println(s"$k=$value")
    }
  }
}
