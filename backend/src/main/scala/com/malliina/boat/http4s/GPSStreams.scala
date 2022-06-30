package com.malliina.boat.http4s

import cats.effect.kernel.Resource
import cats.effect.IO
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

  def resource(db: GPSSource): Resource[IO, GPSStreams] =
    for
      gps <- Resource.eval(build(db))
      _ <- Stream.emit(()).concurrently(gps.publisher).compile.resource.lastOrError
    yield gps

  private def build(db: GPSSource): IO[GPSStreams] =
    for
      in <- Topic[IO, InputEvent]
      saved <- Topic[IO, SavedEvent]
    yield GPSStreams(db, in, saved)

class GPSStreams(val db: GPSSource, val in: Topic[IO, InputEvent], saved: Topic[IO, SavedEvent]):
  val deviceState = GPSManager()
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

  def clientEvents(formatter: TimeFormatter): Stream[IO, FrontEvent] =
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

  private def saveRecovered(coord: GPSCoord): IO[List[GPSInserted]] =
    db.saveCoords(coord).map { inserted => List(GPSInserted(coord, inserted)) }.handleErrorWith {
      t =>
        log.error(s"Unable to save coords.", t)
        IO.pure(Nil)
    }
