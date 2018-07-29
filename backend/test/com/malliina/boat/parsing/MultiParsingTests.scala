package com.malliina.boat.parsing

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.malliina.boat.{BoatId, BoatName, BoatToken, JoinedTrack, KeyedSentence, RawSentence, SentenceKey, TrackId, TrackMetaShort, TrackName}
import com.malliina.file.FileUtilities
import com.malliina.measure.Distance
import com.malliina.values.{UserId, Username}
import tests.BaseSuite

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object MultiParsingTests {
  def testFrom2 = JoinedTrack(
    TrackId(1), TrackName("test"), Instant.now,
    BoatId(1), BoatName("boat"), BoatToken("a"),
    UserId(1), Username("u"), None, 1,
    None, None, None, None, None, Option(Distance.zero)).strip

  def testFrom = TrackMetaShort(
    TrackId(1), TrackName("test"),
    BoatId(1), BoatName("boat"),
    UserId(1), Username("u")
  )

  def listSink[T]: Sink[T, Future[List[T]]] = Sink.fold[List[T], T](Nil)(_ :+ _)
}

class MultiParsingTests extends BaseSuite {
  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()

  //  val testFile = FileUtilities.userHome.resolve(".boat/nmea0183-standard.log")
  val testFile = FileUtilities.userHome.resolve(".boat/Log2.txt")

  val from = MultiParsingTests.testFrom

  def sentences: Seq[RawSentence] =
    Files.readAllLines(testFile, StandardCharsets.UTF_8).asScala.map(RawSentence.apply)

  ignore("parse dates and coords") {
    val flow = Flow[RawSentence].mapConcat(raw => BoatParser.parse(KeyedSentence(SentenceKey(1), raw, from)).toOption.toList)
    val singleParsed = Source(sentences.toList).via(flow)
    val multiParsed = BoatParser.multi(singleParsed)
    val task = multiParsed.runWith(Sink.foreach(println))
    await(task, 30.seconds)
  }

  ignore("just parse") {
    val flow = Flow[RawSentence].mapConcat(raw => BoatParser.parse(KeyedSentence(SentenceKey(1), raw, from)).toOption.toList)
    val singleParsed = Source(sentences.toList).via(flow)

    val hm = singleParsed.runWith(MultiParsingTests.listSink[ParsedSentence])
    val maxSpeed = await(hm, 600.seconds).collect {
      case ParsedBoatSpeed(knots, _) => knots
    }.max
  }

  ignore("process") {
    val intSink = Sink.foreach[Int] { i => i + 1 }
    val stringSink = intSink.contramap[String] { s => s.length }
    val stringToInt = Flow[String].map(s => s.length)
    val target: Sink[Int, Future[Done]] = ???
    Sink.foreach[String](println)
  }
}
