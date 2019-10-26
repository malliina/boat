package com.malliina.boat.db

import java.time.Instant

import com.malliina.boat.{BoatName, BoatToken, CombinedCoord, Coord, DateVal, DeviceId, JoinedBoat, JoinedTrack, MonthVal, RawSentence, SentencePointLink, SentenceRow, TrackId, TrackMeta, TrackName, TrackPointRow, YearVal}
import com.malliina.measure.DistanceM
import com.malliina.values.{UserId, Username}
import io.getquill.NamingStrategy
import io.getquill.context.Context
import io.getquill.idiom.Idiom

import scala.concurrent.duration.FiniteDuration

trait Quotes[I <: Idiom, N <: NamingStrategy] { this: Context[I, N] =>
  val dateOf = quote { i: Instant =>
    infix"DATE($i)".as[DateVal]
  }
  val dateOfOpt = quote { i: Option[Instant] =>
    infix"DATE($i)".as[Option[DateVal]]
  }
  val monthOf = quote { i: Instant =>
    infix"MONTH($i)".as[MonthVal]
  }
  val yearOf = quote { i: Instant =>
    infix"YEAR($i)".as[YearVal]
  }
  val yearOfOpt = quote { i: Option[Instant] =>
    infix"YEAR($i)".as[Option[YearVal]]
  }
  val secondsDiff = quote { (start: Instant, end: Instant) =>
    infix"TIMESTAMPDIFF(SECOND,$start,$end)".as[FiniteDuration]
  }
  val distanceCoords = quote { (from: Coord, to: Coord) =>
    infix"ST_Distance_Sphere($from,$to)".as[DistanceM]
  }
  val boatsTable = quote(querySchema[BoatRow]("boats"))
  val usersTable = quote(querySchema[UserRow]("users"))
  val tracksTable = quote(querySchema[TrackRow]("tracks"))
  val rawPointsTable = quote(querySchema[TrackPointRow]("points"))
  val pointsTable = quote(querySchema[TrackPointRow]("points").map { r =>
    CombinedCoord(
      r.id,
      r.longitude,
      r.latitude,
      r.coord,
      r.boatSpeed,
      r.waterTemp,
      r.depthm,
      r.depthOffsetm,
      r.boatTime,
      dateOf(r.boatTime),
      r.track,
      r.added
    )
  })
  val sentencesTable = quote(querySchema[SentenceRow]("sentences"))
  val sentencePointsTable = quote(querySchema[SentencePointLink]("sentence_points"))
  val now = quote { infix"NOW()".as[Instant] }
  val boatsView = quote {
    boatsTable.join(usersTable).on(_.owner == _.id).map {
      case (b, u) =>
        JoinedBoat(b.id, b.name, b.token, u.id, u.user, u.email, u.language)
    }
  }
  val trackMetas = quote {
    boatsView.join(tracksTable).on(_.device == _.boat).map {
      case (b, t) =>
        TrackMeta(
          t.id,
          t.name,
          t.title,
          t.canonical,
          t.comments,
          t.added,
          t.avgSpeed,
          t.avgWaterTemp,
          t.points,
          t.distance,
          b.device,
          b.boatName,
          b.boatToken,
          b.userId,
          b.username,
          b.email
        )
    }
  }
  val minMaxTimes = quote {
    rawPointsTable.groupBy(_.track).map {
      case (track, rows) =>
        val start = rows.map(_.boatTime).min.getOrElse(infix"NOW()".as[Instant])
        val end = rows.map(_.boatTime).max.getOrElse(infix"NOW()".as[Instant])
        TrackTimes(
          track,
          start,
          end,
          secondsDiff(start, end),
          dateOf(start),
          monthOf(start),
          yearOf(start)
        )
    }
  }
  val topPoints = quote {
    for {
      (track, topSpeed) <- rawPointsTable.groupBy(_.track).map {
        case (t, ps) => (t, ps.map(_.boatSpeed).max)
      }
      (trackId, topPoint) <- rawPointsTable
        .filter(p => track == p.track && topSpeed.contains(p.boatSpeed))
        .groupBy(_.track)
        .map { case (id, winners) => (id, winners.map(_.id).min) }
    } yield TrackTop(trackId, topPoint)
  }
  val topRows = quote {
    pointsTable.join(topPoints).on((p, t) => t.top.contains(p.id)).map(_._1)
  }
  val topRows3 = quote {
    for {
      t <- topPoints
      p <- pointsTable
      if t.top.contains(p.id)
    } yield p
//    pointsTable.join(topPoints).on((p, t) => t.top.contains(p.id)).map(_._1)
  }
  val timedTracks = quote {
    for {
      t <- tracksTable
      times <- minMaxTimes
      if t.id == times.track
    } yield TrackTime(t, times)
  }
  val trackHighlights = quote {
    for {
      topCoord <- topRows
      trackTime <- timedTracks if topCoord.track == trackTime.track.id
    } yield TopTrack(trackTime.track, trackTime.times, topCoord)
  }
  val nonEmptyTracks = quote {
    for {
      boat <- boatsView
      topTrack <- trackHighlights
      if boat.device == topTrack.track.boat
    } yield {
      JoinedTrack(
        topTrack.track.id,
        topTrack.track.name,
        topTrack.track.title,
        topTrack.track.canonical,
        topTrack.track.comments,
        topTrack.track.added,
        boat,
        topTrack.track.points,
        Option(topTrack.times.start),
        topTrack.times.date,
        topTrack.times.month,
        topTrack.times.year,
        Option(topTrack.times.end),
        topTrack.times.duration,
        Option(topTrack.coord.boatSpeed),
        topTrack.track.avgSpeed,
        topTrack.track.avgWaterTemp,
        topTrack.track.distance,
        topTrack.coord
      )
    }
  }
  val rawTrackById = quote { id: TrackId =>
    tracksTable.filter(_.id == id)
  }
  val trackById = quote { id: TrackId =>
    nonEmptyTracks.filter(_.track == id)
  }
  val boatById = quote { id: DeviceId =>
    boatsTable.filter(_.id == id)
  }
  val saveNewBoat = quote { (boat: BoatName, user: UserId, token: BoatToken) =>
    boatsTable
      .insert(_.name -> boat, _.owner -> user, _.token -> token)
      .returningGenerated(_.id)
  }
  val insertSentence = quote { (s: RawSentence, track: TrackId) =>
    sentencesTable
      .insert(_.sentence -> s, _.track -> track)
      .returningGenerated(_.id)
  }
  val namedTrack = quote { name: TrackName =>
    nonEmptyTracks.filter(_.trackName == name)
  }
  val pointsQuery = quote { track: TrackName =>
    for {
      p <- pointsTable
      t <- tracksTable
      if p.track == t.id && t.name == track
    } yield p
  }
//  val rangedCoords = quote { (from: Option[Instant], to: Option[Instant]) =>
//    rawPointsTable.filter { p =>
//      from.forall(f => p.added >= f) && to.forall(t => p.added <= t)
//    }
//  }
  val tracksBy = quote { user: Username =>
    nonEmptyTracks.filter { t =>
      t.boat.username == user
    }
  }
}
