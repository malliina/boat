package com.malliina.boat.parsing

import com.malliina.boat.RawSentence
import net.sf.marineapi.nmea.parser.{SentenceFactory, UnsupportedSentenceException}
import net.sf.marineapi.nmea.sentence.Sentence

trait BoatSentenceParser {
  def parse(in: RawSentence): Either[SentenceError, Sentence]
}

object DefaultParser {
  def apply() = new DefaultParser(SentenceFactory.getInstance())
}

class DefaultParser(parser: SentenceFactory) extends BoatSentenceParser {
  override def parse(in: RawSentence): Either[SentenceError, Sentence] =
    try {
      Right(parser.createParser(in.sentence))
    } catch {
      case use: UnsupportedSentenceException =>
        Left(UnknownSentence(in, use.getMessage))
      case other: Exception =>
        Left(SentenceException(in, other))
    }
}

sealed trait SentenceError {
  def sentence: RawSentence

  def message: String
}

case class UnknownSentence(sentence: RawSentence, detailedMessage: String) extends SentenceError {
  override def message: String = s"Unknown sentence: '$sentence'. $detailedMessage"
}

case class SentenceException(sentence: RawSentence, e: Exception) extends SentenceError {
  override def message: String = s"Error for sentence: '$sentence'. ${e.getMessage}"
}
