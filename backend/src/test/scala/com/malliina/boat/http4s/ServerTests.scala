package com.malliina.boat.http4s

import org.http4s.Status
import tests.{MUnitSuite, ServerSuite}

class ServerTests extends MUnitSuite with ServerSuite {
  test("can call server") {
    val tools = server()
    val client = tools.client
    val status = client.statusFromUri(tools.baseHttpUri.addPath("/health")).unsafeRunSync()
    assertEquals(status, Status.Ok)
  }
}
