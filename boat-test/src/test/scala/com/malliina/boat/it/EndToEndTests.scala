package com.malliina.boat.it

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp.IncomingConnection
import akka.stream.scaladsl.{Keep, Sink, Source, Tcp}
import akka.stream.{ActorMaterializer, KillSwitches}
import akka.util.ByteString
import com.malliina.boat.SentencesMessage
import com.malliina.boat.client.{BoatAgent, TcpClient}
import com.malliina.http.FullUrl
import play.api.libs.json.JsValue

import scala.concurrent.Promise

class EndToEndTests extends BoatTests {
  implicit val as = ActorSystem("test")
  implicit val mat = ActorMaterializer()

  val sentences = Seq(
    "$GPGGA,162112,6009.0969,N,02453.4521,E,1,12,0.70,6,M,19.5,M,,*6F",
    "$GPGGA,174239,6010.2076,N,02450.5518,E,1,12,0.50,0,M,19.5,M,,*63",
    "$GPGGA,124943,6009.5444,N,02448.4491,E,1,12,0.60,0,M,19.5,M,,*61",
    "$GPGGA,125642,6009.2559,N,02447.5942,E,1,12,0.60,1,M,19.5,M,,*68"
  )

  test("plotter to frontend") {
    // the client validates maximum frame length, so we must not concatenate multiple sentences
    val plotterOutput = Source(sentences.map(s => ByteString(s"$s${TcpClient.sentenceDelimiter}", StandardCharsets.US_ASCII)).toList)

    val tcpHost = "127.0.0.1"
    val tcpPort = 10104
    val incomingSink = Sink.foreach[IncomingConnection] { conn =>
      conn.flow.runWith(plotterOutput.concat(Source.maybe), Sink.foreach(msg => println(msg)))
    }
    val plotter = Tcp().bind(tcpHost, tcpPort).viaMat(KillSwitches.single)(Keep.right).toMat(incomingSink)(Keep.left).run()
    val serverUrl = FullUrl.ws(s"localhost:$port", reverse.boats().toString)
    //    val serverUrl = FullUrl.wss("boat.malliina.com", reverse.boats().toString)
    val agent = new BoatAgent(tcpHost, tcpPort, serverUrl)
    val p = Promise[JsValue]()
    val p2 = Promise[SentencesMessage]()

    def handle(json: JsValue): Unit = {
      p.trySuccess(json)
      json.asOpt[SentencesMessage].foreach { sm => p2.trySuccess(sm) }
    }

    val msg = withViewer(handle) { socket =>
      await(socket.initialConnection)
      agent.connect()
      await(p.future)
      await(p2.future)
    }
    assert(msg.sentences.map(_.sentence) === sentences)
    agent.close()
    plotter.shutdown()
  }

  ignore("unreliable plotter connection") {
    // the client validates maximum frame length, so we must not concatenate multiple sentences
    val plotterOutput = Source(sentences.map(s => ByteString(s"$s${TcpClient.sentenceDelimiter}", StandardCharsets.US_ASCII)).toList)

    val tcpHost = "127.0.0.1"
    val tcpPort = 10104
    val incomingSink = Sink.foreach[IncomingConnection] { conn =>
      conn.flow.runWith(plotterOutput.concat(Source.maybe), Sink.foreach(msg => println(msg)))
    }
    val plotter = Tcp().bind(tcpHost, tcpPort).viaMat(KillSwitches.single)(Keep.right).toMat(incomingSink)(Keep.left).run()
    val serverUrl = FullUrl.ws(s"localhost:$port", reverse.boats().toString)
    //    val serverUrl = FullUrl.wss("boat.malliina.com", reverse.boats().toString)
    val agent = new BoatAgent(tcpHost, tcpPort, serverUrl)
    val p = Promise[JsValue]()
    val p2 = Promise[SentencesMessage]()

    def handle(json: JsValue): Unit = {
      p.trySuccess(json)
      json.asOpt[SentencesMessage].foreach { sm => p2.trySuccess(sm) }
    }

    val msg = withViewer(handle) { socket =>
      await(socket.initialConnection)
      agent.connect()
      await(p.future)
      await(p2.future)
    }
    assert(msg.sentences.map(_.sentence) === sentences)
    agent.close()
    plotter.shutdown()
  }
}
