package com.malliina.polestar

import cats.effect.IO
import com.malliina.http.OkClient
import com.malliina.polestar.Polestar.Creds
import com.malliina.values.{Password, Username}

class PolestarTests extends munit.CatsEffectSuite:
  val http = ResourceFunFixture(Polestar.resource[IO])

  http.test("Generate verifier".ignore): client =>
    val v = client.generateVerifier
    assertEquals(v.length, 43)

  http.test("Fetch resume path".ignore): client =>
    client
      .resumePath(client.generateState, client.generateVerifier)
      .map: v =>
        println(v)
        assertEquals(v.length, 43)

  http.test("Code".ignore): client =>
    client
      .code(Creds(Username("todo"), Password("todo")))
      .map: code =>
        println(code)
        assertEquals(1, 1)

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
