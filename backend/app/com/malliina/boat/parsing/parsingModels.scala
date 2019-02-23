package com.malliina.boat.parsing

import java.time.{LocalDate, LocalTime, ZoneOffset}

import com.malliina.boat.{
  Coord,
  Instants,
  KeyedSentence,
  RawSentence,
  SentenceKey,
  TimedCoord,
  TrackId,
  TrackMetaShort,
  TrackPointId,
  TrackRef
}
import com.malliina.measure.{Distance, Speed, Temperature}
import net.sf.marineapi.nmea.parser.{
  DataNotAvailableException,
  SentenceFactory,
  UnsupportedSentenceException
}
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
  def sentence: KeyedSentence

  def from: TrackMetaShort = sentence.from

  def track: TrackId = from.track

  def key: SentenceKey = sentence.key
}

case class ParsedCoord(coord: Coord, ggaTime: LocalTime, sentence: KeyedSentence)
    extends ParsedSentence {
  def lng = coord.lng

  def lat = coord.lat

  def complete(date: LocalDate,
               time: LocalTime,
               boatSpeed: Speed,
               waterTemp: Temperature,
               depth: Distance,
               depthOffset: Distance,
               parts: Seq[SentenceKey]): FullCoord =
    FullCoord(coord, time, date, boatSpeed, waterTemp, depth, depthOffset, from, parts)
}

case class ParsedDateTime(date: LocalDate, time: LocalTime, sentence: KeyedSentence)
    extends ParsedSentence

case class ParsedBoatSpeed(speed: Speed, sentence: KeyedSentence) extends ParsedSentence

case class ParsedWaterSpeed(speed: Speed, sentence: KeyedSentence) extends ParsedSentence

case class WaterTemperature(temp: Temperature, sentence: KeyedSentence) extends ParsedSentence

case class WaterDepth(depth: Distance, offset: Distance, sentence: KeyedSentence)
    extends ParsedSentence

case class FullCoord(coord: Coord,
                     time: LocalTime,
                     date: LocalDate,
                     boatSpeed: Speed,
                     waterTemp: Temperature,
                     depth: Distance,
                     depthOffset: Distance,
                     from: TrackMetaShort,
                     parts: Seq[SentenceKey] = Nil) {
  val dateTime = date.atTime(time)
  val boatTime = dateTime.toInstant(ZoneOffset.UTC)

  def lng = coord.lng

  def lat = coord.lat

  def timed(id: TrackPointId): TimedCoord = TimedCoord(
    id,
    coord,
    Instants.formatDateTime(boatTime),
    boatTime.toEpochMilli,
    Instants.formatTime(boatTime),
    boatSpeed,
    waterTemp,
    depth,
    Instants.timing(boatTime)
  )
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

case class SuspectTime(sentence: RawSentence) extends SentenceError {
  override def message =
    s"Suspect time in '$sentence'. This might mean the plotter is still initializing."
}

case class SentenceFailure(sentence: RawSentence, e: Exception) extends SentenceError {
  override def message: String = s"Error for sentence: '$sentence'. ${e.getMessage}"
}
