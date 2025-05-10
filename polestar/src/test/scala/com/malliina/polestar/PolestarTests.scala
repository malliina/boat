package com.malliina.polestar

import cats.effect.IO
import ch.qos.logback.classic.Level
import com.malliina.logback.LogbackUtils
import com.malliina.values.RefreshToken

class PolestarTests extends munit.CatsEffectSuite:
  val creds = PolestarConfig.conf
  val tokens = ResourceFunFixture(Polestar.httpResource[IO].map(http => TokenClient(http)))
  val http = ResourceFunFixture(Polestar.resource[IO](creds))

  override def beforeAll(): Unit =
    LogbackUtils.init(rootLevel = Level.INFO)
    super.beforeAll()

  tokens.test("Generate verifier".ignore): client =>
    val v = client.generateVerifier
    assertEquals(v.length, 43)

  tokens.test("Tokens".ignore): client =>
    client
      .fetchTokens(creds)
      .map: tokens =>
        println(s"Tokens '$tokens'.")
        assertEquals(1, 1)

  tokens.test("Refresh token".ignore): client =>
    val token = RefreshToken("changeme")
    client
      .refresh(token)
      .map: tokens =>
        println(tokens)
        assertEquals(1, 1)

  http.test("Get graphql".ignore): client =>
    val task = for
      cars <- client.fetchCars()
      car = cars.head
      tele <- client.fetchTelematics(car.vin)
    yield (car, tele)
    task.map: (car, tele) =>
      println(car)
      println(tele)

  val str =
    """
      | Object.assign(window.globalContext, {
      |        pageId: "login",
      |        authnMessageKey: "$escape.escape($authnMessageKey)",
      |        authMessage: "$escape.escape($authMessage)",
      |        errorMessageKey: "$escape.escape($errorMessageKey)",
      |        serverError: "$escape.escape($serverError)",
      |        loginFailed: "false",
      |        trackingId: "tid:QEXWshUSKo4_xt9AAe_RK_wJT5E",
      |        transactionId: "aGv0RZ4Pu9QpVqcATZrxRzgQ1",
      |        username: "",
      |        usernameEditable: "true",
      |        action: "/as/kgis8e0qyx/resume/as/authorization.ping",
      |        brand: "Polestar"
      |    });""".stripMargin

  test("Find action"):
    val action = str match
      case s"""${init}window.globalContext${json}action: "${path}",${rest}""" => path
    assertEquals(action, "/as/kgis8e0qyx/resume/as/authorization.ping")
