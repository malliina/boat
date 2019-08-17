package com.malliina.boat.client

import java.nio.charset.StandardCharsets
import java.nio.file.Paths

import akka.stream.scaladsl.{FileIO, Sink, Source, Tcp}
import akka.util.ByteString

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

/** Sequence of events:
  *
  * <ol>
  *   <li>Open TCP connection to gpsd</li>
  *   <li>Send WATCH command to subscribe to NMEA sentences</li>
  *   <li>Receive sentences for further processing</li>
  *</ol>
  */
class GPSTests extends BasicSuite {
  // Subscribes to NMEA messages. By default, nothing happens.
  val watchCommand =
    """?WATCH={"enable":true,"json":false,"nmea":true,"raw":0,"scaled":false,"timing":false,"split24":false,"pps":false}"""
  val watchMessage =
    ByteString(watchCommand + TcpSource.sentenceDelimiter, StandardCharsets.US_ASCII)

  ignore("receive sentences from GPS source") {
    val client = TcpSource("10.0.0.4", 2947)
    try {
      client.connect(Source.single(watchMessage).concat(Source.maybe[ByteString]))
      val task = client.sentencesHub.take(100).runForeach { msg =>
        msg.sentences.foreach(println)
      }
      await(task)
    } finally {
      client.close()
    }
  }

  ignore("write to file") {
    val sampleSink = FileIO.toPath(Paths.get("gps.txt"))
    val init = Source.single(watchMessage).concat(Source.maybe[ByteString])
    val io = init.via(Tcp().outgoingConnection("10.0.0.4", 2947).take(200)).runWith(sampleSink)
    await(io)
  }

  def await[T](f: Future[T], duration: Duration = 10.seconds): T = Await.result(f, duration)
}
