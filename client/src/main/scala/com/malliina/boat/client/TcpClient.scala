package com.malliina.boat.client

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.pattern.after
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.{Done, NotUsed}
import com.malliina.boat.SentencesMessage
import com.malliina.boat.client.TcpClient.log

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object TcpClient {
  private val log = Logging(getClass)

  val sentenceDelimiter = "\r\n"

  def apply(host: String, port: Int, out: JsonSocket, as: ActorSystem, mat: Materializer) =
    new TcpClient(host, port, Sink.foreach[SentencesMessage](msg => out.sendMessage(msg)))(as, mat)
}

/**
  * @param out destination of sentences, perhaps a WebSocket
  * @see http://www.catb.org/gpsd/NMEA.html
  */
class TcpClient(host: String, port: Int, out: Sink[SentencesMessage, Future[Done]])(implicit as: ActorSystem, mat: Materializer)
  extends TcpSource(host, port) {

  val infiniteRetriesCount = -1
  private val enabled = new AtomicBoolean(true)
  implicit val ec = mat.executionContext

  def connect(): Future[Done] = sentencesSource.runWith(out).flatMap { done =>
    if (enabled.get()) {
      after(1.second, as.scheduler)(connect())
    } else {
      Future.successful(done)
    }
  }

  def persistentSource: Source[SentencesMessage, NotUsed] =
    sentencesSource.recoverWithRetries[SentencesMessage](infiniteRetriesCount, {
      case t =>
        if (enabled.get()) {
          log.error(s"TCP socket failed. Reconnecting...", t)
          sentencesSource
        } else {
          log.error(s"TCP socket failed.", t)
          Source.failed(t)
        }
    })

  def close(): Unit = enabled.set(false)
}
