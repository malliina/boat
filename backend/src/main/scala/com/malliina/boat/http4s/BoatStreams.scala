package com.malliina.boat.http4s

import cats.effect.kernel.Resource
import cats.effect.IO
import com.malliina.boat.ais.AISSource
import com.malliina.boat.db.TrackInsertsDatabase
import com.malliina.boat.http4s.BoatStreams.{log, rights}
import com.malliina.boat.parsing.*
import com.malliina.boat.{BoatEvent, BoatJsonError, CoordsEvent, EmptyEvent, FrontEvent, InputEvent, PingEvent, SentencesMessage, TimeFormatter, VesselMessages}
import com.malliina.util.AppLogger
import fs2.concurrent.Topic
import fs2.Stream

object BoatStreams:
  private val log = AppLogger(getClass)

  def apply(db: TrackInsertsDatabase, ais: AISSource): Resource[IO, BoatStreams] =
    for
      streams <- Resource.eval(build(db, ais))
      _ <- Stream.emit(()).concurrently(streams.publisher).compile.resource.lastOrError
    yield streams

  def build(db: TrackInsertsDatabase, ais: AISSource): IO[BoatStreams] = for
    in <- Topic[IO, InputEvent]
    saved <- Topic[IO, SavedEvent]
  yield new BoatStreams(db, ais, in, saved)

  def rights[L, R](src: fs2.Stream[IO, Either[L, R]]): fs2.Stream[IO, R] = src.flatMap { e =>
    e.fold(l => fs2.Stream.empty, r => fs2.Stream(r))
  }

class BoatStreams(
  db: TrackInsertsDatabase,
  ais: AISSource,
  val boatIn: Topic[IO, InputEvent],
  saved: Topic[IO, SavedEvent]
):
  private val trackState = TrackManager()
  private val sentencesSource = boatIn
    .subscribe(100)
    .collect { case be @ BoatEvent(message, from) =>
      be
    }
    .map { boatEvent =>
      BoatParser
        .read[SentencesMessage](boatEvent.message)
        .map(_.toTrackEvent(boatEvent.from.short))
        .left
        .map { err =>
          log.warn(s"Parse error $err for $boatEvent")
          BoatJsonError(err, boatEvent)
        }
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
    .flatMap { list => Stream.emits(list) }
  val inserted = emittable
    .mapAsync(1) { coord =>
      saveRecovered(coord)
    }
    .flatMap { list => Stream.emits(list) }
  val publisher = inserted.evalMap { i =>
    saved.publish1(i)
  }

  def clientEvents(formatter: TimeFormatter): Stream[IO, FrontEvent] =
    val boatEvents = saved
      .subscribe(100)
      .collect { case i @ Inserted(_, _) =>
        i
      }
      .map { ip =>
        CoordsEvent(
          List(ip.coord.timed(ip.inserted.point, formatter)),
          ip.inserted.track.strip(formatter)
        )
      }
    val aisEvents = ais.slow.map { pairs => VesselMessages(pairs.map(_.toInfo(formatter))) }
    boatEvents.mergeHaltBoth[IO, FrontEvent](aisEvents)

  private def saveRecovered(coord: FullCoord): IO[List[Inserted]] =
    db.saveCoords(coord).map { inserted => List(Inserted(coord, inserted)) }.handleErrorWith { t =>
      log.error(s"Unable to save coords.", t)
      IO.pure(Nil)
    }
