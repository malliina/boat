package com.malliina.boat.http4s

import cats.effect.kernel.Resource
import cats.effect.{Async, IO, Sync}
import cats.syntax.all.{catsSyntaxApplicativeError, toFlatMapOps, toFunctorOps}
import com.malliina.boat.db.GPSSource
import com.malliina.boat.http4s.BoatStreams.rights
import com.malliina.boat.http4s.GPSStreams.log
import com.malliina.boat.parsing.*
import com.malliina.boat.{DeviceEvent, DeviceJsonError, EmptyEvent, FrontEvent, GPSCoordsEvent, InputEvent, SentencesMessage, TimeFormatter}
import com.malliina.util.AppLogger
import fs2.concurrent.Topic
import fs2.Stream

object GPSStreams:
  private val log = AppLogger(getClass)

  def resource[F[_]: Async](db: GPSSource[F]): Resource[F, GPSStreams[F]] =
    for
      gps <- Resource.eval(build(db))
      _ <- Stream.emit(()).concurrently(gps.publisher).compile.resource.lastOrError
    yield gps

  private def build[F[_]: Async](db: GPSSource[F]): F[GPSStreams[F]] =
    for
      in <- Topic[F, InputEvent]
      saved <- Topic[F, SavedEvent]
    yield GPSStreams(db, in, saved)

class GPSStreams[F[_]: Async](
  val db: GPSSource[F],
  val in: Topic[F, InputEvent],
  saved: Topic[F, SavedEvent]
):
  val F = Sync[F]
  private val deviceState = GPSManager()
  val sentencesSource = in
    .subscribe(100)
    .collect { case be @ DeviceEvent(_, _) =>
      be
    }
    .map { boatEvent =>
      boatEvent.message
        .as[SentencesMessage]
        .map(_.toGpsEvent(boatEvent.from))
        .left
        .map(err => DeviceJsonError(err, boatEvent))
    }
  private val sentences = rights(sentencesSource)
  val emittable = sentences
    .mapAsync(1) { s =>
      db.saveSentences(s).map { keyed =>
        BoatParser.parseMultiGps(keyed).toList.flatMap { s =>
          // Because mapAsync(1), we can do non-thread-safe state management here
          deviceState.update(s)
        }
      }
    }
    .flatMap { list => Stream(list*) }
  val inserted = emittable
    .mapAsync(1) { coord =>
      saveRecovered(coord)
    }
    .flatMap { list => Stream(list*) }
  val publisher = inserted.evalMap { i =>
    saved.publish1(i)
  }

  def clientEvents(formatter: TimeFormatter): Stream[F, FrontEvent] =
    saved
      .subscribe(100)
      .collect { case i @ GPSInserted(_, _) =>
        i
      }
      .map { point =>
        GPSCoordsEvent(
          List(point.coord.timed(point.inserted.point, formatter)),
          point.inserted.from.strip
        )
      }

  private def saveRecovered(coord: GPSCoord): F[List[GPSInserted]] =
    db.saveCoords(coord).map { inserted => List(GPSInserted(coord, inserted)) }.handleErrorWith {
      t =>
        log.error(s"Unable to save coords.", t)
        F.pure(Nil)
    }
