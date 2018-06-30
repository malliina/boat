package com.malliina.boat.parsing

import java.time.{LocalDate, LocalTime, ZoneOffset}

import com.malliina.boat.{Coord, Instants, RawSentence, TimedCoord, TrackRef}
import net.sf.marineapi.nmea.parser.{DataNotAvailableException, SentenceFactory, UnsupportedSentenceException}
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
        Left(SentenceFailure(in, other))
    }
}

sealed trait ParsedSentence {
  def from: TrackRef
}

case class ParsedCoord(coord: Coord, time: LocalTime, from: TrackRef) extends ParsedSentence {
  def lng = coord.lng

  def lat = coord.lat

  def withDate(date: LocalDate): DatedCoord = DatedCoord(coord, time, date, from)
}

case class ParsedDate(date: LocalDate, from: TrackRef) extends ParsedSentence

case class DatedCoord(coord: Coord, time: LocalTime, date: LocalDate, from: TrackRef) {
  val dateTime = date.atTime(time)
  val boatTime = dateTime.toInstant(ZoneOffset.UTC)

  def lng = coord.lng

  def lat = coord.lat

  def timed = TimedCoord(coord, Instants.format(boatTime), boatTime.toEpochMilli)
}

sealed trait SentenceError {
  def sentence: RawSentence

  def message: String
}

case class MissingData(sentence: RawSentence, e: DataNotAvailableException) extends SentenceError {
  override def message: String = s"Data not available in '$sentence'. ${e.getMessage}"
}

case class UnknownSentence(sentence: RawSentence, detailedMessage: String) extends SentenceError {
  override def message: String = s"Unknown sentence: '$sentence'. $detailedMessage"
}

case class SentenceFailure(sentence: RawSentence, e: Exception) extends SentenceError {
  override def message: String = s"Error for sentence: '$sentence'. ${e.getMessage}"
}

