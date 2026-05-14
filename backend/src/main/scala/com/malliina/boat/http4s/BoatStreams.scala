package com.malliina.boat.http4s

import cats.data.NonEmptyList
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
import fs2.concurrent.Topic.Closed

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
      _ <- streams.locationsInserter.runInBackground
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
      locations <- Topic[F, NonEmptyList[PointInsert]]
    yield BoatStreams(db, aisDb, ais, in, locations, saved)

  def rights[F[_], L, R](src: Stream[F, Either[L, R]]): Stream[F, R] = src.flatMap: e =>
    e.fold(_ => Stream.empty, r => Stream(r))

class BoatStreams[F[_]: Async](
  db: TrackInsertsDatabase[F],
  aisDb: VesselDatabase[F],
  ais: AISSource[F],
  val boatIn: Topic[F, InputEvent],
  val locationsIn: Topic[F, NonEmptyList[PointInsert]],
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
  val publisher: Stream[F, Either[Closed, Unit]] = inserted.evalMap(i => saved.publish1(i))

  val locationsInserter: Stream[F, List[InsertedPoint]] =
    locationsIn
      .subscribe(maxQueued = 1000)
      .mapAsync(1): list =>
        saveAndPublish(list)
          .map(_.toList)
          .handleError: t =>
            log.error(s"Failed to save ${list.size} locations.", t)
            Nil

  def clientEvents(formatter: TimeFormatter): Stream[F, FrontEvent] =
    val events = saved
      .subscribe(maxQueued = 100)
      .flatMap:
        case InsertedCoord(coord, inserted) =>
          val e = CoordsEvent(
            List(coord.timed(inserted.point, formatter)),
            inserted.track.strip(formatter)
          )
          Stream(e)
        case InsertedCoords(coords) =>
          val byTrack = coords.foldLeft(Vector.empty[CoordsEvent])((acc, ic) =>
            val newCoord = ic.coord.timed(ic.inserted.point, formatter)
            val idx = acc.indexWhere(_.from.track == ic.inserted.track.track)
            if idx >= 0 then
              val old = acc(idx)
              acc.updated(idx, old.copy(coords = old.coords :+ newCoord))
            else acc :+ CoordsEvent(List(newCoord), ic.inserted.track.strip(formatter))
          )
          Stream.emits(byTrack)
        case _ =>
          Stream.empty
    val aisEvents = ais.slow.map(pairs => VesselMessages(pairs.map(_.toInfo(formatter))))
    events.mergeHaltBoth[F, FrontEvent](aisEvents)

  def saveAndPublish(coords: NonEmptyList[PointInsert]): F[NonEmptyList[InsertedPoint]] =
    for
      inserteds <- db.saveCoords(coords)
      result <- saved.publish1(InsertedCoords(inserteds))
    yield
      result.fold(
        _ => log.warn(s"Topic was closed, could not publish car event."),
        _ => ()
      )
      inserteds.map(_.inserted)

  private def saveRecovered(coord: FullCoord): F[List[InsertedCoord]] =
    db.saveCoord(coord)
      .map: inserted =>
        log.debug(s"Inserted $inserted")
        List(InsertedCoord(coord, inserted))
      .handleErrorWith: t =>
        log.error(s"Unable to save coords.", t)
        F.pure(Nil)
