package com.malliina.boat.parsing

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.malliina.boat.{DeviceId, GPSKeyedSentence, GPSSentenceKey, RawSentence}
import tests.BaseSuite

import scala.jdk.CollectionConverters.CollectionHasAsScala

class GPSProcessorTests extends BaseSuite {
  implicit val as = ActorSystem()

  val testFile = Paths.get("gps.txt")

  def sentences: Seq[RawSentence] =
    Files.readAllLines(testFile, StandardCharsets.UTF_8).asScala.toList.map(RawSentence.apply)

  val samples = Seq(
    "$GPZDA,150016.000,17,08,2019,,*51",
    "$GPRMC,150016.000,A,6009.1753,N,02453.2470,E,0.00,166.59,170819,,,A*68",
    "$GPGGA,150016.000,6009.1753,N,02453.2470,E,1,11,0.77,-13.9,M,19.6,M,,*79",
    "$GPGSA,A,3,12,01,03,14,31,18,17,32,11,19,23,,1.14,0.77,0.83*0C",
    "$GPGRS,150016.000,1,-24.0,23.5,18.8,28.5,8.71,5.88,21.1,15.8,8.88,15.5,18.7,*40",
    "$GPGST,150016.000,00020,008.0,006.2,145.0,007.4,006.8,00036*62",
    "$GPTXT,01,01,02,ANTSTATUS=OPEN*2B",
    "$GPGSV,4,1,13,22,78,221,14,01,64,183,22,03,58,257,34,14,51,074,35*71"
  ).map(RawSentence.apply)

  test("parse samples") {
    val flow = Flow[RawSentence].mapConcat { raw =>
      BoatParser.parseGps(GPSKeyedSentence(GPSSentenceKey(1), raw, DeviceId(0))).toOption.toList
    }
    val task = Source(samples.toList).via(flow).via(BoatParser.gpsFlow()).runWith(Sink.seq)
    val seq: Seq[GPSCoord] = await(task)
    assert(seq.exists(_.time.getHour == 15))
    assert(seq.exists(_.date.getDayOfMonth == 17))
  }

  test("read".ignore) {
    samples.foreach { s =>
      val res = SentenceParser.parse(s)
      println(res)
    }
  }

  test("parse ZDA") {
    val in = RawSentence("$GPZDA,213918.000,19,08,2019,,*5C")
    val result = SentenceParser.parse(in)
    assert(result.isRight)
  }
}
