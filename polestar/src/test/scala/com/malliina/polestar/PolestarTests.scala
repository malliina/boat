package com.malliina.polestar

import cats.effect.IO
import ch.qos.logback.classic.Level
import com.malliina.logback.LogbackUtils
import com.malliina.values.{AccessToken, RefreshToken}

class PolestarTests extends munit.CatsEffectSuite:
  val creds = PolestarConfig.conf
  val polestar = ResourceFunFixture(Polestar.resource[IO])

  override def beforeAll(): Unit =
    LogbackUtils.init(rootLevel = Level.INFO)
    super.beforeAll()

  polestar.test("Generate verifier".ignore): client =>
    val v = client.auth.generateVerifier
    assertEquals(v.length, 43)

  polestar.test("Tokens".ignore): client =>
    client.auth
      .fetchTokens(creds)
      .map: tokens =>
        println(s"Tokens '$tokens'.")
        assertEquals(1, 1)

  polestar.test("Refresh token".ignore): client =>
    val token = RefreshToken("changeme")
    client.auth
      .refresh(token)
      .map: tokens =>
        println(tokens)
        assertEquals(1, 1)

  polestar.test("Get graphql".ignore): client =>
    val token = AccessToken("todo")
    val task = for
      cars <- client.fetchCars(token)
      car = cars.head
      tele <- client.fetchTelematics(car.vin, token)
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

  test("Decode telematics"):
    val response =
      scala.io.Source.fromResource("com/malliina/polestar/get-telematics-response.json").mkString
    import io.circe.parser.decode
    val decoded = decode[TelematicsResponse](response)
    println(decoded)
