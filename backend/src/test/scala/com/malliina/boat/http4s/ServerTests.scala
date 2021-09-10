package com.malliina.boat.http4s

import cats.effect.IO
import com.malliina.boat.Errors
import org.http4s.{Request, Status}
import tests.{MUnitSuite, ServerSuite}

class ServerTests extends MUnitSuite with ServerSuite:
  test("can call server") {
    val uri = baseUrl.addPath("health")
    val status = client.statusFromUri(uri).unsafeRunSync()
    assertEquals(status, Status.Ok)
  }

  test("call with no creds") {
    val res = client
      .run(Request[IO](uri = baseUrl.addPath("my-track")))
      .use(res => IO.pure(res))
      .unsafeRunSync()
    assertEquals(res.status, Status.NotFound)
    val errors = res.as[Errors].unsafeRunSync()
    assertEquals(errors.message, Auth.noCredentials)
  }

  test("apple app association") {
    assertEquals(status(".well-known/apple-app-site-association"), Status.Ok)
    assertEquals(status(".well-known/assetlinks.json"), Status.Ok)
  }

  private def status(path: String) =
    val url = baseUrl.addPath(path)
    client.statusFromUri(url).unsafeRunSync()

  def baseUrl = server().baseHttpUri
  def client = server().client
