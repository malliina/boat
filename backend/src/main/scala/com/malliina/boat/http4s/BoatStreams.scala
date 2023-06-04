package com.malliina.boat.http4s

import cats.effect.kernel.Resource
import cats.effect.{Async, Sync}
import cats.syntax.all.{catsSyntaxApplicativeError, catsSyntaxFlatMapOps, toFlatMapOps, toFunctorOps}
import com.malliina.boat.ais.AISSource
import com.malliina.boat.db.{TrackInsertsDatabase, VesselDatabase}
import com.malliina.boat.http4s.BoatStreams.{log, rights}
import com.malliina.boat.parsing.*
import com.malliina.boat.{BoatEvent, BoatJsonError, CoordsEvent, FrontEvent, InputEvent, SentencesMessage, TimeFormatter, VesselMessages}
import com.malliina.util.AppLogger
import fs2.Stream
import fs2.concurrent.Topic

object BoatStreams:
  private val log = AppLogger(getClass)

  def resource[F[_]: Async](
    db: TrackInsertsDatabase[F],
    aisDb: VesselDatabase[F],
    ais: AISSource[F]
  ): Resource[F, BoatStreams[F]] =
    for
      streams <- Resource.eval(build[F](db, aisDb, ais))
      _ <- Stream.emit(()).concurrently(streams.publisher).compile.resource.lastOrError
      _ <- Stream.emit(()).concurrently(streams.saveableAis).compile.resource.lastOrError
    yield streams

  def build[F[_]: Async](
    db: TrackInsertsDatabase[F],
    aisDb: VesselDatabase[F],
    ais: AISSource[F]
  ): F[BoatStreams[F]] =
    for
      in <- Topic[F, InputEvent]
      saved <- Topic[F, SavedEvent]
    yield BoatStreams(db, aisDb, ais, in, saved)

  def rights[F[_], L, R](src: Stream[F, Either[L, R]]): Stream[F, R] = src.flatMap { e =>
    e.fold(l => Stream.empty, r => Stream(r))
  }

class BoatStreams[F[_]: Async](
  db: TrackInsertsDatabase[F],
  aisDb: VesselDatabase[F],
  ais: AISSource[F],
  val boatIn: Topic[F, InputEvent],
  saved: Topic[F, SavedEvent]
):
  val F = Sync[F]
  private val trackState = TrackManager()
  private val sentencesSource = boatIn
    .subscribe(maxQueued = 100)
    .collect { case be @ BoatEvent(_, _) =>
      be
    }
    .map { boatEvent =>
      boatEvent.message
        .as[SentencesMessage]
        .map(_.toTrackEvent(boatEvent.from.short))
        .left
        .map { err =>
          log.warn(s"Parse error $err for $boatEvent")
          BoatJsonError(err, boatEvent)
        }
    }
  val sentences = rights(sentencesSource)
  private val emittable = sentences
    .mapAsync(1) { s =>
      db.saveSentences(s).map { keyed =>
        BoatParser.parseMulti(keyed).toList.flatMap { s =>
          // Because mapAsync(1), we can do non-thread-safe state management here
          trackState.update(s)
        }
      }
    }
    .flatMap { list => Stream.emits(list) }
  private val inserted = emittable
    .mapAsync(1) { coord =>
      saveRecovered(coord)
    }
    .flatMap { list => Stream.emits(list) }
  val saveableAis =
    ais.slow.map { pairs =>
      VesselMessages(pairs.map(_.toInfo(TimeFormatter.en)))
    }.mapAsync(1) { batch =>
      F.delay(log.debug(s"Handling batch of ${batch.vessels.length} vessel events.")) >> aisDb.save(
        batch.vessels
      )
    }.flatMap { list => Stream.emit(list) }
      .handleErrorWith { t =>
        Stream.eval(F.delay(log.error(s"Failed to insert AIS batch. Aborting.", t))) >> Stream.empty
      }
  val publisher = inserted.evalMap { i =>
    saved.publish1(i)
  }

  def clientEvents(formatter: TimeFormatter): Stream[F, FrontEvent] =
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
    boatEvents.mergeHaltBoth[F, FrontEvent](aisEvents)

  private def saveRecovered(coord: FullCoord): F[List[Inserted]] =
    db.saveCoords(coord).map { inserted => List(Inserted(coord, inserted)) }.handleErrorWith { t =>
      log.error(s"Unable to save coords.", t)
      F.pure(Nil)
    }
