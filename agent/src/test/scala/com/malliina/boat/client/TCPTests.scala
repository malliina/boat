package com.malliina.boat.client

import cats.effect.IO
import com.comcast.ip4s.{host, port}

class TCPTests extends munit.CatsEffectSuite:
  test("Stream live messages".ignore):
    val host = host"192.168.77.11"
    val port = port"10110"
    for
      client <- TCPClient.default[IO](host, port)
      connection <- client.connect().compile.drain.start
      msgs <- client.sentencesHub
        .take(10)
        .compile
        .toList
      _ <- client.close
    yield
      val messages = msgs.flatMap(_.sentences)
      messages foreach println
      assert(messages.nonEmpty)
