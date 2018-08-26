package com.malliina.boat.parsing

import java.io.FileInputStream

import com.malliina.util.FileUtils
import net.sf.marineapi.nmea.event.{SentenceEvent, SentenceListener}
import net.sf.marineapi.nmea.io.{ExceptionListener, SentenceReader}
import net.sf.marineapi.nmea.parser.{SentenceFactory, SentenceParser}
import net.sf.marineapi.nmea.sentence.TalkerId
import net.sf.marineapi.provider.PositionProvider
import net.sf.marineapi.provider.event.{PositionEvent, PositionListener}
import org.scalatest.FunSuite

class ParsingTests extends FunSuite {
  val testFile = FileUtils.userHome.resolve(".boat/nmea0183-standard.log")

  ignore("stream") {
    val inStream = new FileInputStream(testFile.toFile)
    val reader = new SentenceReader(inStream)
    SentenceFactory.getInstance().registerParser("AAM", classOf[AAMParser])
    SentenceFactory.getInstance().registerParser("GLC", classOf[GLCParser])
    reader.setExceptionListener(new ExceptionListener {
      override def onException(e: Exception): Unit = () // println(e.getMessage)
    })
    val provider = new PositionProvider(reader)
    provider.addListener(PosListener)
    reader.start()
    Thread.sleep(10000)

    //    reader.setExceptionListener(new ExceptionListener {
    //      override def onException(e: Exception): Unit = println(e.getMessage)
    //    })
    //    val listener = SimpleListener { event =>
    //      if (event.getSentence.getSentenceId == SentenceId.GGA.name()) {
    //        val gga = event.getSentence.asInstanceOf[GGASentence]
    //        println(gga.getPosition)
    //      }
    //    }
    //    reader.addSentenceListener(listener)
    //    reader.start()
  }
}

object PosListener extends PositionListener {
  override def providerUpdate(positionEvent: PositionEvent): Unit = println(positionEvent)
}

class GLCParser(nmea: String) extends SentenceParser(nmea, "GLC") {
  def this(tid: TalkerId) = this("")
}

class AAMParser(nmea: String) extends SentenceParser(nmea, "AAM") {
  def this(tid: TalkerId) = this("")
}

object SimpleListener {
  val ignoring = apply(_ => ())

  def apply(handle: SentenceEvent => Unit): SimpleListener = new SimpleListener {
    override def sentenceRead(event: SentenceEvent): Unit = handle(event)
  }
}

trait SimpleListener extends SentenceListener {
  override def readingPaused(): Unit = ()

  override def readingStarted(): Unit = ()

  override def readingStopped(): Unit = ()

  override def sentenceRead(event: SentenceEvent): Unit
}
