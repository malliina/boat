package com.malliina.boat.http4s

import cats.effect.{Concurrent, IO}
import com.malliina.boat.db.GPSSource
import com.malliina.boat.http4s.BoatStreams.rights
import com.malliina.boat.http4s.GPSStreams.log
import com.malliina.boat.parsing._
import com.malliina.boat.{DeviceEvent, DeviceJsonError, EmptyEvent, FrontEvent, GPSCoordsEvent, InputEvent, SentencesMessage, TimeFormatter}
import com.malliina.util.AppLogger
import fs2.concurrent.Topic

object GPSStreams {
  private val log = AppLogger(getClass)

  def apply(db: GPSSource)(implicit c: Concurrent[IO]): IO[GPSStreams] = for {
    in <- Topic[IO, InputEvent](EmptyEvent)
    saved <- Topic[IO, SavedEvent](EmptySavedEvent)
  } yield new GPSStreams(db, in, saved)
}

class GPSStreams(val db: GPSSource, val in: Topic[IO, InputEvent], saved: Topic[IO, SavedEvent])(
  implicit c: Concurrent[IO]
) {
  val deviceState = GPSManager()
  val sentencesSource = in
    .subscribe(100)
    .drop(1)
    .collect {
      case be @ DeviceEvent(_, _) => be
    }
    .map { boatEvent =>
      BoatParser
        .read[SentencesMessage](boatEvent.message)
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
    .flatMap { list => fs2.Stream(list: _*) }
  val inserted = emittable
    .mapAsync(1) { coord =>
      saveRecovered(coord)
    }
    .flatMap { list => fs2.Stream(list: _*) }
  inserted.evalMap { i =>
    saved.publish1(i)
  }.compile.drain.unsafeRunAsyncAndForget()

  def clientEvents(formatter: TimeFormatter): fs2.Stream[IO, FrontEvent] =
    saved
      .subscribe(100)
      .drop(1)
      .collect {
        case i @ GPSInserted(_, _) => i
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
}
