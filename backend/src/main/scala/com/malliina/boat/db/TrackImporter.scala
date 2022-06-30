package com.malliina.boat.db

import cats.effect.IO
import cats.kernel.Eq
import com.malliina.boat.db.TrackImporter.{dateEq, log}
import com.malliina.boat.parsing.*
import com.malliina.boat.{InsertedPoint, KeyedSentence, RawSentence, SentencesEvent, TrackMetaShort}
import com.malliina.util.AppLogger
import fs2.{Chunk, Pipe, Stream, text}
import fs2.io.file.Path

import java.nio.file.Path as JPath
import java.time.LocalDate
import concurrent.duration.DurationInt

object TrackImporter:
  private val log = AppLogger(getClass)

  implicit val dateEq: Eq[LocalDate] =
    Eq.by[LocalDate, (Int, Int, Int)](d => (d.getYear, d.getMonthValue, d.getDayOfMonth))

class TrackImporter(inserts: TrackInsertsDatabase) extends TrackStreams:

  /** Saves sentences in `file` to the database `track`.
    *
    * @param file
    *   NMEA sentence log
    * @param track
    *   target track
    * @return
    *   number of points saved
    */
  def saveFile(file: Path, track: TrackMetaShort): IO[Long] = save(sentences(file), track)

  def save(source: Stream[IO, RawSentence], track: TrackMetaShort): IO[Long] =
    val describe = s"track ${track.trackName} with boat ${track.boatName} by ${track.username}"
    val task = source
      .filter(_ != RawSentence.initialZda)
      .groupWithin(100, 500.millis)
      .map(chunk => SentencesEvent(chunk.toList, track))
      .through(processor)
      .fold(0) { (acc, point) =>
        if acc == 0 then log.info(s"Saving points to $describe...")
        if acc % 100 == 0 && acc > 0 then log.info(s"Inserted $acc points to $describe...")
        acc + 1
      }

    task.compile.toList.map(_.head)

  private def processor: Pipe[IO, SentencesEvent, InsertedPoint] =
    _.through(sentenceInserter).through(sentenceCompiler).through(pointInserter)

  private def sentenceInserter: Pipe[IO, SentencesEvent, KeyedSentence] =
    _.evalMap(e => inserts.saveSentences(e)).flatMap(kss => Stream.emits(kss))

  private def sentenceCompiler: Pipe[IO, KeyedSentence, FullCoord] =
    val state = TrackManager()
    _.flatMap { sentence =>
      Stream.emits(BoatParser.parseMulti(Seq(sentence)).flatMap(parsed => state.update(parsed)))
    }

  private def pointInserter: Pipe[IO, FullCoord, InsertedPoint] =
    _.mapAsync(1)(coord => inserts.saveCoords(coord))

class TrackStreams:
  def sentencesForDay(file: Path, day: LocalDate): Stream[IO, RawSentence] =
    fileByDate(file).collect { case (date, chunk) if date == day => chunk }.flatMap { chunk =>
      Stream.chunk(chunk)
    }

  def fileByDate(file: Path): Stream[IO, (LocalDate, Chunk[RawSentence])] =
    byDateGrouped(sentences(file))

  def byDate(source: Stream[IO, RawSentence]): Stream[IO, Chunk[RawSentence]] =
    byDateGrouped(source).map(_._2)

  def byDateGrouped(
    source: Stream[IO, RawSentence]
  ): Stream[IO, (LocalDate, Chunk[RawSentence])] =
    source
      .zipWithScan1(LocalDate.of(1980, 1, 1)) { (date, sentence) =>
        zdaDate(sentence).getOrElse(date)
      }
      .groupAdjacentBy(_._2)
      .map { case (date, chunk) => (date, chunk.map(_._1)) }

  def zdaDate(s: RawSentence): Option[LocalDate] = SentenceParser.parse(s).toOption.flatMap {
    case zda @ ZDAMessage(_, _, _, _, _, _, _) => Option(zda.date)
    case _                                     => None
  }
  def sentences(file: Path) = lines(file).map(RawSentence.apply)

  def lines(file: Path) =
    fs2.io.file
      .Files[IO]
      .readAll(file)
      .through(text.utf8.decode)
      .through(text.lines)
