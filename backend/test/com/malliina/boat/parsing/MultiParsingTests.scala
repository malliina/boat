package com.malliina.boat.parsing

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import akka.Done
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.malliina.boat.{BoatName, DeviceId, KeyedSentence, RawSentence, SentenceKey, TrackId, TrackMetaShort, TrackName}
import com.malliina.util.FileUtils
import com.malliina.values.Username
import tests.BaseSuite

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.CollectionHasAsScala

object MultiParsingTests {
  def testFrom = TrackMetaShort(
    TrackId(1),
    TrackName("test"),
    DeviceId(1),
    BoatName("boat"),
    Username("u")
  )

  def listSink[T]: Sink[T, Future[List[T]]] = Sink.fold[List[T], T](Nil)(_ :+ _)
}

class MultiParsingTests extends BaseSuite {
  implicit val as = ActorSystem()
  implicit val mat = Materializer(as)

  //  val testFile = FileUtilities.userHome.resolve(".boat/nmea0183-standard.log")
  val testFile = FileUtils.userHome.resolve(".boat/Log2.txt")

  val from = MultiParsingTests.testFrom

  def sentences: Seq[RawSentence] =
    Files.readAllLines(testFile, StandardCharsets.UTF_8).asScala.toList.map(RawSentence.apply)

  test("parse dates and coords".ignore) {
    val flow = Flow[RawSentence].mapConcat(raw =>
      BoatParser.parse(KeyedSentence(SentenceKey(1), raw, from)).toOption.toList
    )
    val singleParsed = Source(sentences.toList).via(flow)
    val multiParsed = singleParsed.via(BoatParser.multiFlow())
//    val task = multiParsed.runWith(Sink.foreach(println))
    val start = System.currentTimeMillis()
    val task = multiParsed.runWith(Sink.seq)
    val coords = await(task, 30.seconds)
    val end = System.currentTimeMillis()
    println(s"${coords.length} coords in ${end - start} ms")
  }

  test("just parse".ignore) {
    val flow = Flow[RawSentence].mapConcat(raw =>
      BoatParser.parse(KeyedSentence(SentenceKey(1), raw, from)).toOption.toList
    )
    val singleParsed = Source(sentences.toList).via(flow)

    val hm = singleParsed.runWith(MultiParsingTests.listSink[ParsedSentence])
    val maxSpeed = await(hm, 600.seconds).collect {
      case ParsedBoatSpeed(knots, _) => knots
    }.max
  }

  test("process".ignore) {
    val intSink = Sink.foreach[Int] { i => i + 1 }
    val stringSink = intSink.contramap[String] { s => s.length }
    val stringToInt = Flow[String].map(s => s.length)
    val target: Sink[Int, Future[Done]] = ???
    Sink.foreach[String](println)
  }
}
