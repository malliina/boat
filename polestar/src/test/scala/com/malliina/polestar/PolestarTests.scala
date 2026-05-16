package com.malliina.polestar

import cats.effect.IO
import ch.qos.logback.classic.Level
import com.malliina.http.{JsonError, OkHttpResponse, ResponseException, StatusError}
import com.malliina.logback.LogbackUtils
import com.malliina.values.Literals.jwt

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
    val token = jwt"changeme".refresh
    client.auth
      .refresh(token)
      .map: tokens =>
        println(tokens)
        assertEquals(1, 1)

  polestar.test("Get graphql".ignore): client =>
    val token = jwt"todo".access
    val task = for
      cars <- client.fetchCars(token)
      car = cars.head
      tele <- client.fetchTelematics(car.vin, token)
    yield (car, tele)
    task
      .map: (car, tele) =>
        println(car)
        println(tele)
      .handleError:
        case re: ResponseException =>
          re.error match
            case JsonError(error, response, url) =>
              response match
                case r: OkHttpResponse =>
                  println(r.asString)
            case StatusError(response, url) =>
              response match
                case r: OkHttpResponse => println(s"From $url: '${r.asString}'.")
        case _ =>
          println("o")

  polestar.test("Tokens and telematics".ignore): client =>
    client.auth
      .fetchTokens(creds)
      .flatMap: tokens =>
        val token = tokens.accessToken
        val task = for
          cars <- client.fetchCars(token)
          car = cars.head
          tele <- client.fetchTelematics(car.vin, token)
          image: Variables.Image = Variables.Image(
            car.pno34,
            car.structureWeek,
            car.modelYear,
            "fi"
          )
          _ <- client.fetchCarImages(image, token)
        yield (car, tele)
        task
          .map: (car, tele) =>
            println(car)
            println(tele)
          .handleError:
            case re: ResponseException =>
              re.error match
                case JsonError(error, response, url) =>
                  response match
                    case r: OkHttpResponse =>
                      println(r.asString)
                case StatusError(response, url) =>
                  response match
                    case r: OkHttpResponse => println(s"From $url: '${r.asString}'.")
            case _ =>
              println("o")

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
      case s"""${_}window.globalContext${_}action: "${path}",${_}""" => path
    assertEquals(action, "/as/kgis8e0qyx/resume/as/authorization.ping")

  test("Decode telematics"):
    val response =
      scala.io.Source.fromResource("com/malliina/polestar/get-telematics-response.json").mkString
    import io.circe.parser.decode
    val decoded = decode[TelematicsResponse](response)
    assert(decoded.isRight)
