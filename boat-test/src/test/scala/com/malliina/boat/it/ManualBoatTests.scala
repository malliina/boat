package com.malliina.boat.it

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import cats.effect.IO
import com.malliina.boat.{BoatNames, BoatToken, BoatTokens, RawSentence, SentencesMessage}
import com.malliina.http.FullUrl
import com.malliina.util.FileUtils

import scala.jdk.CollectionConverters.CollectionHasAsScala

class ManualBoatTests extends BoatTests:
  //  val testFile = FileUtilities.userHome.resolve(".boat/Log2.txt")
  //  val testFile = FileUtilities.userHome.resolve(".boat/nmea0183-standard.log")
  val testFile = FileUtils.userHome.resolve(".boat/Log.txt")

  def sentences =
    Files.readAllLines(testFile, StandardCharsets.UTF_8).asScala.map(RawSentence.apply)

  // def relevantSentences = sentences.drop(10000).filter(s => s.sentence.startsWith("$GPGGA") || s.sentence.startsWith("$GPZDA"))
  def relevantSentences = sentences

//  def url = FullUrl.ws("localhost:9000", reverse.boats().toString)
  def url = FullUrl.wss("www.boat-tracker.com", reverse.ws.boats.renderString)

  http.test("local GPS reporting".ignore): httpClient =>
    val testMessages = relevantSentences.toList.grouped(1000).map(SentencesMessage.apply).toList
    openRandomBoat(url, httpClient): boat =>
      IO.parTraverseN(1)(testMessages.zipWithIndex): (msg, idx) =>
        println(s"Sending batch $idx/${testMessages.length}...")
        boat.send(msg) >> IO.sleep(5.seconds)

  http.test("slow anon GPS reporting".ignore): httpClient =>
    val testMessages =
      relevantSentences.toList.grouped(30).map(SentencesMessage.apply).slice(50, 100).toList
    openRandomBoat(url, httpClient): boat =>
      IO.parTraverseN(1)(testMessages): msg =>
        boat.send(msg) >> IO.sleep(500.millis)

  http.test("slow GPS reporting".ignore): httpClient =>
    val token = BoatToken("todo")
    val testMessages =
      relevantSentences.toList.grouped(30).map(SentencesMessage.apply).slice(50, 100).toList
    openBoat(url, Right(token), httpClient): boat =>
      IO.parTraverseN(1)(testMessages): msg =>
        boat.send(msg) >> IO.sleep(500.millis)

  test("generate names".ignore):
    val name = BoatNames.random()
    val token = BoatTokens.random()
    println(name)
    println(token)

  http.test("boat can connect".ignore): httpClient =>
    val token = BoatToken("todo")
    openBoat(url, Right(token), httpClient): boat =>
      IO.sleep(10.seconds)
