package com.malliina.boat.db

import java.sql.{Timestamp, Types}
import java.time.Instant

import com.malliina.boat.db.BoatDatabase.fail
import com.malliina.boat.db.NewTracksDatabase.log
import com.malliina.boat.http.{BoatQuery, SortOrder, TrackQuery, TrackSort}
import com.malliina.boat.parsing.FullCoord
import com.malliina.boat.{BoatName, BoatTokens, BoatTrackMeta, Coord, CoordsEvent, DeviceId, DeviceMeta, FullTrack, InsertedPoint, JoinedBoat, JoinedTrack, KeyedSentence, Lang, Language, MinimalUserInfo, SentenceCoord2, SentencesEvent, StatsResponse, TimeFormatter, TrackCanonical, TrackId, TrackInfo, TrackInput, TrackMeta, TrackName, TrackRef, TrackTitle, Tracks, TracksBundle, Utils}
import com.malliina.measure.{DistanceM, SpeedDoubleM, SpeedIntM, SpeedM, Temperature}
import com.malliina.values.{UserId, Username}
import io.getquill._
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object NewTracksDatabase {
  private val log = Logger(getClass)

  def apply(db: BoatDatabase[SnakeCase], isMariaDb: Boolean = false): NewTracksDatabase =
    new NewTracksDatabase(db, isMariaDb)(db.ec)
}

class NewTracksDatabase(val db: BoatDatabase[SnakeCase], isMariaDb: Boolean)(
    implicit val ec: ExecutionContext
) extends TracksSource {
  import db._

  val distanceFunctionName = if (isMariaDb) "ST_Distance" else "ST_Distance_Sphere"
  val selectDistance = quote { (from: Coord, to: Coord) =>
    infix"SELECT #$distanceFunctionName($from,$to)".as[Query[DistanceM]]
  }

  implicit val ie: Encoder[Instant] = encoder(
    Types.TIMESTAMP,
    (idx, value, row) => row.setTimestamp(idx, new Timestamp(value.toEpochMilli))
  )
  val minSpeed: SpeedM = 1.kmh

  // Distributed to another module to reduce compilation times
  val statsSource = StatsDatabase(db)

  def stats(user: MinimalUserInfo, limits: TrackQuery, lang: Lang): Future[StatsResponse] =
    statsSource.stats(user, limits, lang)

  def updateTitle(track: TrackName, title: TrackTitle, user: UserId): Future[JoinedTrack] = Future {
    transaction {
      log.info(s"Updating title of '$track' to '$title'...")
      val ts = run(
        nonEmptyTracks
          .filter(t => t.trackName == lift(track) && t.boat.userId == lift(user))
          .map(_.track)
      )
      val id = ts.headOption.getOrElse(fail(s"Track not found: '$track'."))
      run(
        tracksTable
          .filter(t => t.id == lift(id))
          .update(
            _.canonical -> lift(TrackCanonical(Utils.normalize(title.title))),
            _.title -> lift(Option(title))
          )
      )
      val updated = run(nonEmptyTracks.filter(t => t.track == lift(id))).headOption
        .getOrElse(fail(s"Track ID not found: '$id'."))
      log.info(s"Updated title of '$id' to '$title' normalized to '${updated.canonical}'.")
      updated
    }
  }
  def updateComments(track: TrackId, comments: String, user: UserId): Future[JoinedTrack] = Future {
    transaction {
      log.info(s"Updating comments of '$track' to '$comments'...")
      val ts =
        run(
          nonEmptyTracks
            .filter(t => t.track == lift(track) && t.boat.userId == lift(user))
            .map(_.track)
        )
      val id = ts.headOption.getOrElse(fail(s"Track not found: '$track'."))
      run(tracksTable.filter(_.id == lift(id)).update(_.comments -> lift(Option(comments))))
      val updated = run(trackById(lift(id))).headOption
        .getOrElse(fail(s"Track ID not found: '$id'."))
      log.info(s"Updated comments of '$id' to '$comments'.")
      updated
    }
  }

  def addBoat(boat: BoatName, user: UserId): Future[BoatRow] = Future {
    transaction {
      val addedId = run(saveNewBoat(lift(boat), lift(user), lift(BoatTokens.random())))
      val created = run(boatById(lift(addedId))).headOption
        .getOrElse(fail(s"Boat not found: '$addedId'."))
      log.info(s"Registered boat '$boat' with ID '${created.id}' owned by '$user'.")
      created
    }
  }

  def removeDevice(device: DeviceId, user: UserId): Future[Int] = Future {
    val rows = run {
      boatsTable
        .filter(b => b.owner == lift(user) && b.id == lift(device))
        .delete
    }
    if (rows == 1) log.info(s"Deleted boat '$device' owned by '$user'.")
    else log.warn(s"Boat '$device' owned by '$user' not found.")
    rows.toInt
  }

  def renameBoat(boat: DeviceId, newName: BoatName, user: UserId): Future[BoatRow] = Future {
    val boatFilter = quote {
      boatsView.filter(b => b.userId == lift(user) && b.device == lift(boat))
    }
    transaction {
      val isEmpty = run(boatFilter.isEmpty)
      if (isEmpty)
        fail(s"Boat '$boat' by '$user' not found.")
      run(boatsTable.filter(_.id == lift(boat)).update(_.name -> lift(newName)))
      val updated = run(boatById(lift(boat))).headOption
        .getOrElse(fail(s"Boat not found: '$boat'."))
      log.info(s"Renamed boat '$boat' to '$newName'.")
      updated
    }
  }

  /** If the given track and boat exist and are owned by the user, returns the track info.
    *
    * If the boat exists and is owned by the user but no track with the given name exists, the track is created.
    *
    * If neither the track nor boat exist, they are created.
    *
    * If the track name or boat name is already taken by another user, the returned Future fails.
    *
    * @param meta track, boat and user info
    * @return track specs, or failure if there is a naming clash
    */
  def joinAsBoat(meta: BoatTrackMeta): Future[TrackMeta] = Future {
    transaction {
      val existing = run(
        trackMetas.filter(
          t =>
            t.username == lift(meta.user) && t.boatName == lift(meta.boat) && t.trackName == lift(
              meta.track
            )
        )
      ).headOption
      existing.getOrElse {
        val user = run(usersTable.filter(_.user == lift(meta.user))).headOption
          .getOrElse(fail(s"User not found: '${meta.user}'."))
        val maybeBoat =
          run(boatsTable.filter(b => b.name == lift(meta.boat) && b.owner == lift(user.id))).headOption
        val boat = maybeBoat.getOrElse {
          val exists: Boolean = run(boatsTable.filter(_.name == lift(meta.boat)).nonEmpty)
          if (exists) {
            fail(
              s"Boat name '${meta.boat}' is already taken and therefore not available for '${meta.user}'."
            )
          } else {
            val id = run(saveNewBoat(lift(meta.boat), lift(user.id), lift(BoatTokens.random())))
            run(boatById(lift(id))).headOption.getOrElse(fail(s"Boat not found: '$id'."))
          }
        }
        // Prepares track
        val maybeTrack =
          run(trackMetas.filter(t => t.trackName == lift(meta.track) && t.boat == lift(boat.id))).headOption
        maybeTrack.getOrElse {
          val in = TrackInput.empty(meta.track, boat.id)
          val inserted = run(
            tracksTable
              .insert(
                _.name -> lift(in.name),
                _.boat -> lift(in.boat),
                _.avgSpeed -> lift(in.avgSpeed),
                _.avgWaterTemp -> lift(in.avgWaterTemp),
                _.points -> lift(in.points),
                _.distance -> lift(in.distance),
                _.canonical -> lift(in.canonical)
              )
              .returningGenerated(_.id)
          )
          val track = run(trackMetas.filter(_.track == lift(inserted))).headOption
            .getOrElse(fail(s"Track not found: '$inserted'."))
          log.info(s"Registered track with ID '$inserted' for boat '${boat.id}'.")
          track
        }
      }
    }
  }

  def joinAsDevice(from: DeviceMeta): Future[JoinedBoat] = Future {
    val user = from.user
    val boat = from.boat
    transaction {
      val existing =
        run(boatsView.filter(b => b.username == lift(user) && b.boatName == lift(boat))).headOption
      existing.getOrElse {
        val userRow = run(usersTable.filter(_.user == lift(user))).headOption
          .getOrElse(fail(s"User not found: '$user'."))
        val userId = userRow.id
        val maybeBoat =
          run(boatsView.filter(b => b.boatName == lift(boat) && b.userId == lift(userId))).headOption
        maybeBoat.getOrElse {
          val alreadyExists = run(boatsTable.filter(b => b.name == lift(boat)).nonEmpty)
          if (alreadyExists) {
            fail(s"Boat name '$boat' is already taken and therefore not available for '$user'.")
          }
          val newId = run(
            boatsTable
              .insert(
                _.name -> lift(boat),
                _.token -> lift(BoatTokens.random()),
                _.owner -> lift(userId)
              )
              .returningGenerated(_.id)
          )
          val b = run(boatsView.filter(_.device == lift(newId))).headOption
            .getOrElse(fail(s"Boat not found: '$newId'."))
          log.info(s"Registered boat '${b.boatName}' with ID '${b.device}' owned by '$user'.")
          b
        }
      }
    }
  }

  def saveSentences(sentences: SentencesEvent): Future[Seq[KeyedSentence]] = Future {
    val from = sentences.from
    transaction {
      sentences.sentences.map { s =>
        val id = run(insertSentence(lift(s), lift(from.track)))
        KeyedSentence(id, s, from)
      }
    }
  }

  def saveCoords(coord: FullCoord): Future[InsertedPoint] = Future {
    val track = coord.from.track
    val previous = quote {
      rawPointsTable
        .filter(_.track == lift(track))
        .sortBy(_.trackIndex)(Ord.desc)
        .take(1)
    }
    val pointsQuery = quote(rawPointsTable.filter(_.track == lift(track)))
    val task = for {
      prev <- runIO(previous).map(_.headOption)
      trackIdx = prev.map(_.trackIndex).getOrElse(0) + 1
      diff <- prev.map { p =>
        runIO(selectDistance(lift(p.coord), lift(coord.coord)))
          .map(_.headOption.getOrElse(DistanceM.zero))
      }.getOrElse { IO.successful(DistanceM.zero) }
      point <- runIO {
        rawPointsTable
          .insert(
            _.longitude -> lift(coord.lng),
            _.latitude -> lift(coord.lat),
            _.coord -> lift(coord.coord),
            _.boatSpeed -> lift(coord.boatSpeed),
            _.waterTemp -> lift(coord.waterTemp),
            _.depthm -> lift(coord.depth),
            _.depthOffsetm -> lift(coord.depthOffset),
            _.boatTime -> lift(coord.boatTime),
            _.track -> lift(coord.from.track),
            _.trackIndex -> lift(trackIdx),
            _.diff -> lift(diff)
          )
          .returningGenerated(_.id)
      }
      // .avg returns BigDecimal no matter what
      avgSpeed <- runIO(pointsQuery.filter(_.boatSpeed >= lift(minSpeed)).map(_.boatSpeed).avg)
        .map(decimal => decimal.map(d => d.toDouble.kmh))
      avgWaterTemp <- runIO(pointsQuery.map(_.waterTemp).avg)
        .map(decimal => decimal.map(d => Temperature(d.toDouble)))
      points <- runIO(pointsQuery.size)
      distance <- runIO(pointsQuery.map(_.diff).sum).map(_.getOrElse(DistanceM.zero))
      _ <- runIO(
        rawTrackById(lift(track)).update(
          _.avgWaterTemp -> lift(avgWaterTemp),
          _.avgSpeed -> lift(avgSpeed),
          _.points -> lift(points.toInt),
          _.distance -> lift(distance)
        )
      )
      ref <- runIO(trackById(lift(track)))
    } yield InsertedPoint(point, ref.headOption.getOrElse(fail(s"Track not found: '$track'.")))
    perform("Insert coordinate", task.transactional)
  }

  def tracksFor(user: MinimalUserInfo, filter: TrackQuery): Future[Tracks] = Future {
    val unsorted = quote(tracksBy(lift(user.username)))
    val nameDesc =
      runIO(unsorted.sortBy(t => (t.trackTitle, t.trackName, t.track))(Ord.descNullsLast))
    val nameAsc =
      runIO(unsorted.sortBy(t => (t.trackTitle, t.trackName, t.track))(Ord.ascNullsLast))
    val recentDesc = runIO(unsorted.sortBy(t => (t.start, t.track))(Ord.descNullsLast))
    val recentAsc = runIO(unsorted.sortBy(t => (t.start, t.track))(Ord.ascNullsLast))
    val pointsDesc = runIO(unsorted.sortBy(t => (t.points, t.track))(Ord.descNullsLast))
    val pointsAsc = runIO(unsorted.sortBy(t => (t.points, t.track))(Ord.ascNullsLast))
    val topSpeedDesc = runIO(unsorted.sortBy(t => (t.topSpeed, t.track))(Ord.descNullsLast))
    val topSpeedAsc = runIO(unsorted.sortBy(t => (t.topSpeed, t.track))(Ord.ascNullsLast))
    val lengthDesc = runIO(unsorted.sortBy(t => (t.distance, t.track))(Ord.descNullsLast))
    val lengthAsc = runIO(unsorted.sortBy(t => (t.distance, t.track))(Ord.ascNullsLast))
    val rows =
      if (filter.sort == TrackSort.Name) {
        filter.order match {
          case SortOrder.Desc => nameDesc
          case SortOrder.Asc  => nameAsc
        }
      } else {
        (filter.sort, filter.order) match {
          case (TrackSort.Recent, SortOrder.Desc)   => recentDesc
          case (TrackSort.Recent, SortOrder.Asc)    => recentAsc
          case (TrackSort.Points, SortOrder.Desc)   => pointsDesc
          case (TrackSort.Points, SortOrder.Asc)    => pointsAsc
          case (TrackSort.TopSpeed, SortOrder.Desc) => topSpeedDesc
          case (TrackSort.TopSpeed, SortOrder.Asc)  => topSpeedAsc
          case (TrackSort.Length, SortOrder.Desc)   => lengthDesc
          case (TrackSort.Length, SortOrder.Asc)    => lengthAsc
          case _                                    => recentDesc
        }
      }
    val formatter = TimeFormatter(user.language)
    Tracks(performIO(rows).map(_.strip(formatter)))
  }

  def tracksBundle(user: MinimalUserInfo, filter: TrackQuery, lang: Lang): Future[TracksBundle] = {
    val statsFuture = stats(user, filter, lang)
    val tracksFuture = tracksFor(user, filter)
    for {
      ss <- statsFuture
      ts <- tracksFuture
    } yield TracksBundle(ts.tracks, ss)
  }

  def ref(track: TrackName, language: Language): Future[TrackRef] = Future {
    run(namedTrack(lift(track))).headOption
      .getOrElse(fail(s"Track not found: '$track'."))
      .strip(TimeFormatter(language))
  }
  def canonical(track: TrackCanonical, language: Language): Future[TrackRef] = Future {
    run(nonEmptyTracks.filter(_.canonical == lift(track))).headOption
      .getOrElse(fail(s"Track not found: '$track'."))
      .strip(TimeFormatter(language))
  }

  def track(track: TrackName, user: Username, query: TrackQuery): Future[TrackInfo] = Future {
    transaction {
      val points = quote { pointsQuery(lift(track)) }
      val coords = run(points.sortBy(_.boatTime)(Ord.asc))
      val top = run(points.sortBy(_.boatSpeed)(Ord.desc).take(1)).headOption
      TrackInfo(coords, top)
    }
  }

  def full(track: TrackName, language: Language, query: TrackQuery): Future[FullTrack] = Future {
    val limitedPoints = quote {
      pointsQuery(lift(track))
        .sortBy(p => (p.boatTime, p.id, p.added))(Ord(Ord.asc, Ord.asc, Ord.asc))
        .drop(lift(query.limits.offset))
        .take(lift(query.limits.limit))
    }
    val coordsAction = quote {
      for {
        p <- limitedPoints
        sp <- sentencePointsTable
        if p.id == sp.point
        s <- sentencesTable
        if s.id == sp.sentence
      } yield SentenceCoord2(
        p.id,
        p.lon,
        p.lat,
        p.coord,
        p.boatSpeed,
        p.waterTemp,
        p.depth,
        p.depthOffset,
        p.boatTime,
        p.date,
        p.track,
        p.added,
        s.id,
        s.sentence,
        s.added
      )
    }
    val trackStats =
      run(namedTrack(lift(track))).headOption.getOrElse(fail(s"Track not found: '$track'."))
    val coords = run(
      coordsAction.sortBy(sc => (sc.boatTime, sc.id, sc.sentenceAdded))(
        Ord(Ord.asc, Ord.asc, Ord.asc)
      )
    )
    val formatter = TimeFormatter(language)
    FullTrack(trackStats.strip(formatter), TracksDatabase.collectRows(coords, formatter))
  }

  implicit class InstantQuotes(left: Instant) {
    def >=(right: Instant) = quote(infix"$left >= $right".as[Boolean])
    def <=(right: Instant) = quote(infix"$left <= $right".as[Boolean])
  }

  val rangedCoords = quote { (from: Option[Instant], to: Option[Instant]) =>
    rawPointsTable.filter { p =>
      from.forall(f => p.added >= f) && to.forall(t => p.added <= t)
    }
  }

  def history(user: MinimalUserInfo, limits: BoatQuery): Future[Seq[CoordsEvent]] = Future {
    def trackSql = quote { ts: Query[JoinedTrack] =>
      val q = for {
        t <- ts
        c <- rangedCoords(lift(limits.from), lift(limits.to))
        if t.track == c.track
      } yield TrackCoord(t, c)
      q.sortBy(_.row.trackIndex)(Ord.desc)
        .drop(lift(limits.offset))
        .take(lift(limits.limit))
    }
    val defaultEligible = runIO {
      trackSql(
        nonEmptyTracks
          .filter(_.boat.username == lift(user.username))
          .sortBy(_.trackAdded)(Ord.desc)
          .take(1)
      )
    }
    val trackLimited = runIO {
      trackSql(
        nonEmptyTracks.filter(t => liftQuery(limits.tracks).contains(t.trackName))
      )
    }
    val canonicalLimited = runIO {
      trackSql(
        nonEmptyTracks.filter(t => liftQuery(limits.canonicals).contains(t.canonical))
      )
    }
    val fallback = runIO { trackSql(nonEmptyTracks) }
    val eligibleTracks =
      if (limits.tracks.nonEmpty) trackLimited
      else if (limits.canonicals.nonEmpty) canonicalLimited
      else if (limits.newest) defaultEligible
      else fallback
    val keys = (limits.tracks.map(_.name) ++ limits.canonicals.map(_.name)).mkString(", ")
    val describe = if (keys.isEmpty) "" else s"for tracks $keys "
    val rows = perform(s"Track history ${describe}by user ${user.username}", eligibleTracks)
    TracksDatabase.collectTrackCoords(rows, user.language)
  }
}
