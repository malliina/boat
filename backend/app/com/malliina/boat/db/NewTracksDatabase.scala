package com.malliina.boat.db

import com.malliina.boat.{CombinedCoord, CombinedFullCoord, CoordsEvent, Language, SentenceCoord2, SentenceRow, TimeFormatter, TimedCoord}
import play.api.Logger

import scala.concurrent.duration.DurationLong

object NewTracksDatabase {
  private val log = Logger(getClass)

  def collectRows(rows: Seq[SentenceCoord2], formatter: TimeFormatter): Seq[CombinedFullCoord] =
    collect(rows.map(sc => (sc.s, sc.c)), formatter)

  def collect(
    rows: Seq[(SentenceRow, CombinedCoord)],
    formatter: TimeFormatter
  ): Seq[CombinedFullCoord] =
    rows.foldLeft(Vector.empty[CombinedFullCoord]) {
      case (acc, (s, c)) =>
        val idx = acc.indexWhere(_.id == c.id)
        if (idx >= 0) {
          val old = acc(idx)
          acc.updated(
            idx,
            old.copy(sentences = old.sentences :+ s.timed(formatter))
          )
        } else {
          acc :+ c.toFull(Seq(s), formatter)
        }
    }

  def collectTrackCoords(rows: Seq[TrackCoord], language: Language): Seq[CoordsEvent] = {
    val start = System.currentTimeMillis()
    val formatter = TimeFormatter(language)
    val result = rows.foldLeft(Vector.empty[CoordsEvent]) {
      case (acc, tc) =>
        val from = tc.track
        val point = tc.row
        val idx = acc.indexWhere(_.from.track == from.track)
        val instant = point.boatTime
        val coord = TimedCoord(
          point.id,
          point.coord,
          formatter.formatDateTime(instant),
          instant.toEpochMilli,
          formatter.formatTime(instant),
          point.boatSpeed,
          point.waterTemp,
          point.depth,
          formatter.timing(instant)
        )
        if (idx >= 0) {
          val old = acc(idx)
          acc.updated(idx, old.copy(coords = coord :: old.coords))
        } else {
          acc :+ CoordsEvent(List(coord), from.strip(formatter))
        }
    }
    val end = System.currentTimeMillis()
    val duration = (end - start).millis
    if (duration > 500.millis) {
      log.warn(s"Collected ${rows.length} in ${duration.toMillis} ms")
    }
    result
  }
}
