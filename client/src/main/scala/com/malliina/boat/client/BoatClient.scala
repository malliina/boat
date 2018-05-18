package com.malliina.boat.client

import java.io.{BufferedReader, IOException, InputStreamReader}
import java.net.{InetAddress, Socket}

import com.malliina.boat.client.BoatClient.log
import com.malliina.boat.{RawSentence, SentencesMessage}
import com.malliina.http.FullUrl

import scala.collection.mutable

object BoatClient {
  private val log = Logging(getClass)

  def apply(boatAddress: InetAddress, port: Int, headers: Seq[KeyValue]) = {
    val url = FullUrl.wss("boat.malliina.com", "/ws/boats")
    val out = new JsonSocket(url, CustomSSLSocketFactory.forHost("boat.malliina.com"), headers)
    new BoatClient(boatAddress, port, out)
  }
}

class BoatClient(boatAddress: InetAddress, port: Int, out: JsonSocket) {
  private val batch = mutable.Buffer[RawSentence]()
  private var lastSentence = System.currentTimeMillis()

  private var reader: Option[BufferedReader] = None

  restart()

  def restart(): Unit = {
    reader.foreach(_.close())
    reader = Option(connect())
    listen()
  }

  private def connect(): BufferedReader = {
    try {
      newReader()
    } catch {
      case ioe: IOException =>
        log.warn(s"Unable to connect. Reconnecting in 5000 ms.", ioe)
        Thread.sleep(4000)
        connect()
    }
  }

  private def newReader(): BufferedReader = {
    val socket = new Socket(boatAddress, port)
    val reader = new InputStreamReader(socket.getInputStream)
    new BufferedReader(reader)
  }

  private def listen(): Unit = {
    try {
      if (shouldSendBatch()) sendBatch()
      reader.flatMap(r => Option(r.readLine())).map { line =>
        batch.append(RawSentence(line))
        lastSentence = System.currentTimeMillis()
      }.getOrElse {
        Thread.sleep(100)
      }
      listen()
    } catch {
      case ioe: IOException =>
        log.error(s"Connection to ${boatAddress.getHostAddress} failed. Reconnecting in 1000 ms.", ioe)
        Thread.sleep(1000)
        restart()
    }

  }

  private def shouldSendBatch(): Boolean =
    batch.nonEmpty && (System.currentTimeMillis() - lastSentence > 1000)

  private def sendBatch(): Unit = {
    out.sendMessage(SentencesMessage(batch.toList))
    batch.clear()
  }
}
