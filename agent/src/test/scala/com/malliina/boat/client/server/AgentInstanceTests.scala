package com.malliina.boat.client.server

import cats.effect.IO
import com.comcast.ip4s.{host, port}
import com.malliina.http.io.HttpClientIO
import fs2.Stream

class AgentInstanceTests extends munit.CatsEffectSuite:
  test("Connect with conf".ignore):
    val resource = for
      http <- HttpClientIO.resource[IO]
      agent <- AgentInstance.resource(http)
    yield agent
    val task = resource.use: agent =>
      val start = agent.updateIfNecessary(
        BoatConf(
          host"1.1.1.1",
          port"11111",
          Device.BoatDevice,
          token = None,
          enabled = true
        )
      )
      agent.connections.concurrently(Stream.eval(start)).compile.drain
    task
      .map: ok =>
        assertEquals(ok, ())
      .handleError: err =>
        println(s"Err $err")
