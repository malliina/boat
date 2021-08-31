package com.malliina.boat.client

import java.nio.file.Paths

import akka.stream.scaladsl.{FileIO, Source, Tcp}
import akka.util.ByteString

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

/** Sequence of events:
  *
  * <ol>
  *   <li>Open TCP connection to gpsd</li>
  *   <li>Send WATCH command to subscribe to NMEA sentences</li>
  *   <li>Receive sentences for further processing</li>
  * </ol>
  */
class GPSTests extends BasicSuite {
  test("receive sentences from GPS source".ignore) {
    val client = TcpSource("10.0.0.4", 2947)
    try {
      client.connect(Source.single(TcpClient.watchMessage).concat(Source.maybe[ByteString]))
      val task = client.sentencesHub.take(100).runForeach { msg =>
        msg.sentences.foreach { s =>
          println(s"${s.value.length} $s")
        }
      }
      await(task)
    } finally {
      client.close()
    }
  }

  test("write to file".ignore) {
    val sampleSink = FileIO.toPath(Paths.get("gps2.txt"))
    val init = Source.single(TcpClient.watchMessage).concat(Source.maybe[ByteString])
    val io = init.via(Tcp().outgoingConnection("10.0.0.4", 2947).take(200)).runWith(sampleSink)
    await(io)
  }
}
