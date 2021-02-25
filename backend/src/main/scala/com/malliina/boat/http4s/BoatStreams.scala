package com.malliina.boat.http4s

import cats.effect.{ContextShift, IO}
import com.malliina.boat.db.TrackInsertsDatabase
import com.malliina.boat.parsing.{BoatParser, FullCoord, Inserted, TrackManager}
import com.malliina.boat.{BoatEvent, BoatJsonError, CoordsEvent, FrontEvent, SentencesMessage, TimeFormatter, VesselMessages}
import com.malliina.util.AppLogger
import fs2.concurrent.Topic
import BoatStreams.log
import com.malliina.boat.ais.AISSource

object BoatStreams {
  private val log = AppLogger(getClass)
}

class BoatStreams(
  db: TrackInsertsDatabase,
  ais: AISSource,
  val boatIn: Topic[IO, BoatEvent],
  viewerOut: Topic[IO, FrontEvent],
  saved: Topic[IO, Inserted]
)(implicit cs: ContextShift[IO]) {
  private val trackState = TrackManager()
  val sentencesSource = boatIn.subscribe(100).map { boatEvent =>
    BoatParser
      .read[SentencesMessage](boatEvent.message)
      .map(_.toTrackEvent(boatEvent.from.short))
      .left
      .map(err => BoatJsonError(err, boatEvent))
  }
  val sentences = rights(sentencesSource)
  val emittable = sentences
    .mapAsync(1) { s =>
      db.saveSentences(s).map { keyed =>
        BoatParser.parseMulti(keyed).toList.flatMap { s =>
          // Because mapAsync(1), we can do non-thread-safe state management here
          trackState.update(s)
        }
      }
    }
    .flatMap { list => fs2.Stream(list: _*) }
  val inserted = emittable
    .mapAsync(1) { coord =>
      saveRecovered(coord)
    }
    .flatMap { list => fs2.Stream(list: _*) }
  inserted.evalMap { i =>
    saved.publish1(i)
  }.compile.drain.unsafeRunAsyncAndForget()
  ais.slow

  def clientEvents(formatter: TimeFormatter): fs2.Stream[IO, FrontEvent] = {
    val boatEvents = saved.subscribe(100).drop(1).map { ip =>
      CoordsEvent(
        List(ip.coord.timed(ip.inserted.point, formatter)),
        ip.inserted.track.strip(formatter)
      )
    }
    val aisEvents = ais.slow.map { pairs => VesselMessages(pairs.map(_.toInfo(formatter))) }
    boatEvents.mergeHaltBoth[IO, FrontEvent](aisEvents)
  }

  private def saveRecovered(coord: FullCoord): IO[List[Inserted]] =
    db.saveCoords(coord).map { inserted => List(Inserted(coord, inserted)) }.handleErrorWith { t =>
      log.error(s"Unable to save coords.", t)
      IO.pure(Nil)
    }

  private def rights[L, R](src: fs2.Stream[IO, Either[L, R]]): fs2.Stream[IO, R] = src.flatMap {
    e => e.fold(l => fs2.Stream.empty, r => fs2.Stream(r))
  }
}
