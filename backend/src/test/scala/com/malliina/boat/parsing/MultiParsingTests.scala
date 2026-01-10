package com.malliina.boat.parsing

import com.malliina.boat.{BaseSuite, BoatName, DeviceId, RawSentence, TrackId, TrackMetaShort, TrackName}
import com.malliina.util.FileUtils
import com.malliina.values.Username
import org.typelevel.ci.CIStringSyntax

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.jdk.CollectionConverters.CollectionHasAsScala

object MultiParsingTests:
  def testFrom = TrackMetaShort(
    TrackId.unsafe(1),
    TrackName.unsafe("test"),
    DeviceId.unsafe(1),
    BoatName.unsafe(ci"boat"),
    Username.unsafe("u")
  )

class MultiParsingTests extends BaseSuite:
  //  val testFile = FileUtilities.userHome.resolve(".boat/nmea0183-standard.log")
  val testFile = FileUtils.userHome.resolve(".boat/Log2.txt")

  val from = MultiParsingTests.testFrom

  def sentences: Seq[RawSentence] =
    Files.readAllLines(testFile, StandardCharsets.UTF_8).asScala.toList.map(RawSentence.unsafe)

//  test("parse dates and coords".ignore) {
//    val flow = Flow[RawSentence].mapConcat(raw =>
//      BoatParser.parse(KeyedSentence(SentenceKey(1), raw, from)).toOption.toList
//    )
//    val singleParsed = Source(sentences.toList).via(flow)
//    val multiParsed = singleParsed.via(BoatParser.multiFlow())
////    val task = multiParsed.runWith(Sink.foreach(println))
//    val start = System.currentTimeMillis()
//    val task = multiParsed.runWith(Sink.seq)
//    val coords = await(task, 30.seconds)
//    val end = System.currentTimeMillis()
//    println(s"${coords.length} coords in ${end - start} ms")
//  }
