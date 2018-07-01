package com.malliina.boat.it

import java.nio.charset.StandardCharsets

import akka.stream.KillSwitches
import akka.stream.scaladsl.Tcp.IncomingConnection
import akka.stream.scaladsl.{Keep, Sink, Source, Tcp}
import akka.util.ByteString
import com.malliina.boat.client.server.BoatConf
import com.malliina.boat.client.{BoatAgent, TcpSource}
import com.malliina.boat.{CoordsEvent, SentencesMessage}
import com.malliina.http.FullUrl
import play.api.libs.json.JsValue

import scala.concurrent.Promise

class EndToEndTests extends BoatTests {
  val sentences = Seq(
    "$GPVTG,51.0,T,42.2,M,2.4,N,4.4,K,A*25",
    "$SDMTW,15.2,C*02",
    "$GPGGA,162112,6009.0969,N,02453.4521,E,1,12,0.70,6,M,19.5,M,,*6F",
    "$GPGGA,174239,6010.2076,N,02450.5518,E,1,12,0.50,0,M,19.5,M,,*63",
    "$GPZDA,141735,04,05,2018,-03,00*69",
    "$GPGGA,124943,6009.5444,N,02448.4491,E,1,12,0.60,0,M,19.5,M,,*61",
    "$GPGGA,125642,6009.2559,N,02447.5942,E,1,12,0.60,1,M,19.5,M,,*68"
  )

  test("plotter to frontend") {
    // the client validates maximum frame length, so we must not concatenate multiple sentences
    val plotterOutput = Source(sentences.map(s => ByteString(s"$s${TcpSource.sentenceDelimiter}", StandardCharsets.US_ASCII)).toList)

    val tcpHost = "127.0.0.1"
    val tcpPort = 10104
    val incomingSink = Sink.foreach[IncomingConnection] { conn =>
      conn.flow.runWith(plotterOutput.concat(Source.maybe), Sink.foreach(msg => println(msg)))
    }
    val (server, plotter) = Tcp().bind(tcpHost, tcpPort).viaMat(KillSwitches.single)(Keep.both).toMat(incomingSink)(Keep.left).run()
    await(server)
    val serverUrl = FullUrl.ws(s"localhost:$port", reverse.boats().toString)
    val agent = BoatAgent(BoatConf.anon(tcpHost, tcpPort), serverUrl)
    try {
      val p = Promise[JsValue]()
      val p3 = Promise[CoordsEvent]()

      val sink = Sink.foreach[JsValue] { json =>
        p.trySuccess(json)
        json.asOpt[CoordsEvent].foreach { ce => p3.trySuccess(ce) }
      }
      openViewerSocket(sink, None) { _ =>
        agent.connect()
        await(p.future)
        await(p3.future).coords
      }
    } finally {
      agent.close()
      plotter.shutdown()
    }
  }

  ignore("external unreliable TCP server") {
    val serverUrl = FullUrl.ws(s"localhost:$port", reverse.boats().toString)
    val conf = BoatConf.anon("127.0.0.1", 10104)
    val agent = BoatAgent(conf, serverUrl)
    try {
      val done = agent.connect()
      await(done, 30.seconds)
    } finally {
      agent.close()
    }
  }

  ignore("unreliable plotter connection") {
    // this test does not work due to nonexistent termination signals of the TCP server

    // the client validates maximum frame length, so we must not concatenate multiple sentences
    val plotterOutput = Source(sentences.map(s => ByteString(s"$s${TcpSource.sentenceDelimiter}", StandardCharsets.US_ASCII)).toList)

    val tcpHost = "127.0.0.1"
    val tcpPort = 10104
    val incomingSink = Sink.foreach[IncomingConnection] { conn =>
      conn.flow.runWith(plotterOutput.concat(Source.maybe), Sink.foreach(msg => println(msg)))
    }
    val server = Tcp().bind(tcpHost, tcpPort).toMat(incomingSink)(Keep.left).run()
    val binding = await(server)
    val serverUrl = FullUrl.ws(s"localhost:$port", reverse.boats().toString)
    //    val serverUrl = FullUrl.wss("boat.malliina.com", reverse.boats().toString)
    val agent = BoatAgent(BoatConf.anon(tcpHost, tcpPort), serverUrl)
    val p = Promise[JsValue]()
    val p2 = Promise[SentencesMessage]()
    val p3 = Promise[SentencesMessage]()
    val sink = Sink.foreach[JsValue] { json =>
      p.trySuccess(json)
      json.asOpt[SentencesMessage].foreach { sm => if (!p2.trySuccess(sm)) p3.trySuccess(sm) }
    }
    openViewerSocket(sink, None) { _ =>
      agent.connect()
      await(p.future)
      val msg = await(p2.future)
      assert(msg.sentences.map(_.sentence) === sentences)
      // Simulates loss of plotter connectivity
      await(binding.unbind())
      println("Plotter disconnected.")
      val rePromise = Promise[Int]()
      val incomingSinkAgain = Sink.foreach[IncomingConnection] { conn =>
        println("TCP connection again.")
        rePromise.trySuccess(1)
        conn.flow.runWith(plotterOutput.concat(Source.maybe), Sink.foreach(msg => println(msg)))
      }
      val (serverAgain, _) = Tcp().bind(tcpHost, tcpPort).toMat(incomingSinkAgain)(Keep.both).run()
      val bindingAgain = await(serverAgain)
      println("Online again.")
      try {
        await(rePromise.future)
      } finally {
        await(bindingAgain.unbind())
        agent.close()
      }
    }

  }
}
