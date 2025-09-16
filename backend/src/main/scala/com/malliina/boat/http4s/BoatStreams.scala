package com.malliina.boat.http4s

import cats.effect.kernel.Resource
import cats.effect.{Async, Sync}
import cats.syntax.all.{catsSyntaxApplicativeError, catsSyntaxFlatMapOps, toFlatMapOps, toFunctorOps}
import com.malliina.boat.ais.AISSource
import com.malliina.boat.db.{TrackInsertsDatabase, VesselDatabase}
import com.malliina.boat.http4s.BoatStreams.{log, rights}
import com.malliina.boat.parsing.*
import com.malliina.boat.{BoatEvent, BoatJsonError, CoordsEvent, FrontEvent, InputEvent, InsertedPoint, SentencesMessage, TimeFormatter, VesselMessages}
import com.malliina.tasks.runInBackground
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
      _ <- streams.publisher.runInBackground
      _ <- streams.saveableAis.runInBackground
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

  def rights[F[_], L, R](src: Stream[F, Either[L, R]]): Stream[F, R] = src.flatMap: e =>
    e.fold(_ => Stream.empty, r => Stream(r))

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
    .collect:
      case be @ BoatEvent(_, _, _) =>
        be
    .map: boatEvent =>
      boatEvent.message
        .as[SentencesMessage]
        .map(_.toTrackEvent(boatEvent.from.short, boatEvent.userAgent))
        .left
        .map: err =>
          log.warn(s"Parse error $err for $boatEvent")
          BoatJsonError(err, boatEvent)
  val sentences = rights(sentencesSource)
  private val emittable = sentences
    .mapAsync(1): s =>
      log.debug(s"Saving sentences $s...")
      db.saveSentences(s)
        .map: keyed =>
          BoatParser
            .parseMulti(keyed)
            .toList
            .flatMap: parsed =>
              // Because mapAsync(1), we can do non-thread-safe state management here
              trackState.update(parsed, s.userAgent)
    .flatMap(list => Stream.emits(list))
  private val inserted = emittable
    .mapAsync(1)(coord => saveRecovered(coord))
    .flatMap(list => Stream.emits(list))
  val saveableAis =
    ais.slow
      .map(pairs => VesselMessages(pairs.map(_.toInfo(TimeFormatter.en))))
      .mapAsync(1): batch =>
        F.delay(log.debug(s"Handling batch of ${batch.vessels.length} vessel events.")) >> aisDb
          .save(
            batch.vessels
          )
      .flatMap(list => Stream.emit(list))
      .handleErrorWith: t =>
        Stream.eval(F.delay(log.error(s"Failed to insert AIS batch. Aborting.", t))) >> Stream.empty
  val publisher = inserted.evalMap(i => saved.publish1(i))

  def clientEvents(formatter: TimeFormatter): Stream[F, FrontEvent] =
    val events = saved
      .subscribe(maxQueued = 100)
      .collect:
        case InsertedCoord(coord, inserted) =>
          CoordsEvent(
            List(coord.timed(inserted.point, formatter)),
            inserted.track.strip(formatter)
          )
    val aisEvents = ais.slow.map(pairs => VesselMessages(pairs.map(_.toInfo(formatter))))
    events.mergeHaltBoth[F, FrontEvent](aisEvents)

  def saveCarCoord(coord: CarCoord): F[InsertedPoint] =
    for
      inserted <- db.saveCoords(coord)
      result <- saved.publish1(InsertedCoord(coord, inserted))
    yield
      result.fold(
        _ => log.warn(s"Topic was closed, could not publish car event."),
        _ => ()
      )
      inserted

  private def saveRecovered(coord: FullCoord): F[List[InsertedCoord]] =
    db.saveCoords(coord)
      .map: inserted =>
        log.debug(s"Inserted $inserted")
        List(InsertedCoord(coord, inserted))
      .handleErrorWith: t =>
        log.error(s"Unable to save coords.", t)
        F.pure(Nil)
