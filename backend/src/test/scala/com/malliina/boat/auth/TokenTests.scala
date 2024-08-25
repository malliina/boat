package com.malliina.boat.auth

import cats.effect.IO
import com.malliina.boat.{BaseSuite, LocalConf}
import com.malliina.config.ConfigNode
import com.malliina.http.FullUrl
import com.malliina.http.HttpClient
import com.malliina.values.IdToken
import com.malliina.web.{AppleTokenValidator, ClientId, GoogleAuthFlow, SignInWithApple}

import java.nio.file.Path
import java.time.Instant
import scala.jdk.CollectionConverters.MapHasAsScala

class TokenTests extends BaseSuite:
  val boatConf = LocalConf.conf.parse[ConfigNode]("boat").toOption.get

  http.test("google token validation".ignore): httpClient =>
    val in = "token_here"
    val client = GoogleAuthFlow.keyClient(Seq(ClientId("client_id")), httpClient)
    client
      .validate(IdToken(in))
      .map: outcome =>
        assert(outcome.isRight)
        val v = outcome.toOption.get
        v.parsed.claims.getClaims.asScala.foreach: (k, value) =>
          println(s"$k=$value")

  http.test("validate iOS SIWA token".ignore): client =>
    val token = IdToken("changeme")
    val v = AppleTokenValidator.app(client)
    v.validateToken(token, Instant.now())
      .map: _ =>
        ()

  http.test("validate siwa code".ignore): client =>
    val fields = Map(
      "code" -> demoConf("code"),
      "grant_type" -> "authorization_code"
    )
    printRequest(fields, client)

  http.test("validate refresh token".ignore): client =>
    val fields = Map(
      "grant_type" -> "refresh_token",
      "refresh_token" -> demoConf("refresh")
    )
    printRequest(fields, client)

  def demoConf(key: String) =
    LocalConf.localConf.parse[String](s"boat.demo.$key").toOption.get

  def printRequest(fields: Map[String, String], client: HttpClient[IO]): IO[Unit] =
    val siwaConf = boatConf
      .parse[Path]("apple.privateKey")
      .map(p => SignInWithApple.Conf.siwa(true, p))
      .fold(err => throw err, identity)
      .copy(clientId = AppleTokenValidator.boatClientId)
    val siwa = SignInWithApple(siwaConf)
    val privateKey = siwa.signInWithAppleToken(Instant.now())
    val _ = boatConf.parse[ConfigNode]("demo").toOption.get
    client
      .postForm(
        FullUrl("https", "appleid.apple.com", "/auth/token"),
        Map(
          "client_id" -> siwaConf.clientId.value,
          "client_secret" -> privateKey.value
        ) ++ fields
      )
      .map(res => println(res.asString))
