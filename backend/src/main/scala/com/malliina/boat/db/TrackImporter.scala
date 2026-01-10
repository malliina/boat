package com.malliina.boat.db

import cats.effect.Temporal
import cats.kernel.Eq
import cats.syntax.all.toFunctorOps
import com.malliina.boat.db.TrackImporter.{dateEq, log}
import com.malliina.boat.parsing.*
import com.malliina.boat.{InsertedPoint, InsertedSentences, RawSentence, SentencesEvent, TrackMetaShort, UserAgent}
import com.malliina.util.AppLogger
import fs2.{Chunk, Pipe, Stream, text}
import fs2.io.file.{Files, Path}

import java.time.LocalDate
import concurrent.duration.DurationInt

object TrackImporter:
  private val log = AppLogger(getClass)

  given dateEq: Eq[LocalDate] =
    Eq.by[LocalDate, (Int, Int, Int)](d => (d.getYear, d.getMonthValue, d.getDayOfMonth))

class TrackImporter[F[_]: { Files, Temporal }](inserts: TrackInsertsDatabase[F])
  extends TrackStreams[F]:

  /** Saves sentences in `file` to the database `track`.
    *
    * @param file
    *   NMEA sentence log
    * @param track
    *   target track
    * @return
    *   number of points saved
    */
  def saveFile(file: Path, track: TrackMetaShort): F[Long] = save(sentences(file), track, None)

  def save(
    source: Stream[F, RawSentence],
    track: TrackMetaShort,
    userAgent: Option[UserAgent]
  ): F[Long] =
    val describe = s"track ${track.trackName} with boat ${track.boatName} by ${track.username}"
    val start = System.currentTimeMillis()
    val task = source
      .filter(_ != RawSentence.initialZda)
      .groupWithin(100, 500.millis)
      .map(chunk => SentencesEvent(chunk.toList, track, userAgent))
      .through(processor)
      .fold(0): (acc, _) =>
        val duration = 1.0d * (System.currentTimeMillis() - start) / 1000d
        val pps = if duration > 0 then 1.0d * acc / duration else 0
        if acc == 0 then log.info(s"Saving points to $describe...")
        if acc % 100 == 0 && acc > 0 then
          log.info(s"Inserted $acc points to $describe. Pace is $pps points/s.")
        acc + 1

    task.compile.toList.map(_.head)

  private def processor: Pipe[F, SentencesEvent, InsertedPoint] =
    _.through(
      _.evalMap(e => inserts.saveSentences(e).map(kss => InsertedSentences(kss, e.userAgent)))
    )
      .through(sentenceCompiler)
      .through(_.mapAsync(1)(coord => inserts.saveCoords(coord)))

  private def sentenceCompiler: Pipe[F, InsertedSentences, FullCoord] =
    val state = TrackManager()
    _.flatMap: sentences =>
      Stream.emits(
        BoatParser
          .parseMulti(sentences.sentences)
          .flatMap(parsed => state.update(parsed, sentences.userAgent))
      )

class TrackStreams[F[_]: Files]:
  def sentencesForDay(file: Path, day: LocalDate): Stream[F, RawSentence] =
    fileByDate(file)
      .collect:
        case (date, chunk) if date == day =>
          chunk
      .flatMap: chunk =>
        Stream.chunk(chunk)

  def fileByDate(file: Path): Stream[F, (LocalDate, Chunk[RawSentence])] =
    byDateGrouped(sentences(file))

  def byDate(source: Stream[F, RawSentence]): Stream[F, Chunk[RawSentence]] =
    byDateGrouped(source).map(_._2)

  private def byDateGrouped(
    source: Stream[F, RawSentence]
  ): Stream[F, (LocalDate, Chunk[RawSentence])] =
    source
      .zipWithScan1(LocalDate.of(1980, 1, 1)): (date, sentence) =>
        zdaDate(sentence).getOrElse(date)
      .groupAdjacentBy(_._2)
      .map((date, chunk) => (date, chunk.map(_._1)))

  private def zdaDate(s: RawSentence): Option[LocalDate] = SentenceParser
    .parse(s)
    .toOption
    .flatMap:
      case zda @ ZDAMessage(_, _, _, _, _, _, _) => Option(zda.date)
      case _                                     => None
  def sentences(file: Path) = lines(file).flatMap(l => fs2.Stream.fromOption(RawSentence.build(l).toOption))

  private def lines(file: Path) =
    fs2.io.file
      .Files[F]
      .readAll(file)
      .through(text.utf8.decode)
      .through(text.lines)
