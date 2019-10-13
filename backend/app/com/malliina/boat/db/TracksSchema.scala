package com.malliina.boat.db

import java.time.Instant

import com.malliina.boat._
import com.malliina.boat.db.BoatSchema.CreatedTimestampType
import com.malliina.boat.parsing.GPSFix
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.{Email, UserId, Username}
import play.api.Logger

import scala.concurrent.ExecutionContext
import TracksSchema.log

object TracksSchema {
  private val log = Logger(getClass)
}

trait TracksSchema extends Mappings with DatabaseClient {
  self: JdbcComponent with QueryModels =>
  import api._

  val usersTable = TableQuery[UsersTable]
  val boatsTable = TableQuery[BoatsTable]
  val tracksTable = TableQuery[TracksTable]
  val sentencesTable = TableQuery[SentencesTable]
  val pointsTable = TableQuery[TrackPointsTable]
  val sentencePointsTable = TableQuery[SentencesPointsLink]

  val gpsSentencesTable = TableQuery[GPSSentencesTable]
  val gpsPointsTable = TableQuery[GPSTable]
  val gpsSentencePointsTable = TableQuery[GPSSentencesPointsLink]
  val gpsSentenceInserts =
    gpsSentencesTable.map(_.forInserts).returning(gpsSentencesTable.map(_.id))
  val gpsPointInserts =
    gpsPointsTable.map(_.forInserts).returning(gpsPointsTable.map(_.id))

  val sentenceInserts =
    sentencesTable.map(_.forInserts).returning(sentencesTable.map(_.id))
  val boatInserts = boatsTable.map(_.forInserts).returning(boatsTable.map(_.id))
  val userInserts = usersTable.map(_.forInserts).returning(usersTable.map(_.id))
  val trackInserts =
    tracksTable.map(_.forInserts).returning(tracksTable.map(_.id))
  val coordInserts =
    pointsTable.map(_.forInserts).returning(pointsTable.map(_.id))

  val dateOf: Rep[Instant] => Rep[DateVal] =
    SimpleFunction.unary[Instant, DateVal](jdbc.date)
  val distanceCoords =
    SimpleFunction.binary[Coord, Coord, DistanceM](jdbc.distance)

  val boatsView: Query[LiftedJoinedBoat, JoinedBoat, Seq] =
    boatsTable.join(usersTable).on(_.owner === _.id).map {
      case (b, u) =>
        LiftedJoinedBoat(
          b.id,
          b.name,
          b.token,
          u.id,
          u.user,
          u.email,
          u.language
        )
    }

  class SentencesTable(tag: Tag) extends Table[SentenceRow](tag, "sentences") {
    def id = column[SentenceKey]("id", O.AutoInc, O.PrimaryKey)
    def sentence = column[RawSentence]("sentence", O.Length(128))
    def track = column[TrackId]("track")
    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def userConstraint = foreignKey("sentences_track_fk", track, tracksTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )

    def forInserts =
      (sentence, track) <> ((SentenceInput.apply _).tupled, SentenceInput.unapply)
    def * =
      (id, sentence, track, added) <> ((SentenceRow.apply _).tupled, SentenceRow.unapply)
  }

  class GPSSentencesTable(tag: Tag)
      extends Table[GPSSentenceRow](tag, "gps_sentences") {
    def id = column[GPSSentenceKey]("id", O.AutoInc, O.PrimaryKey)
    def sentence = column[RawSentence]("sentence", O.Length(128))
    def device = column[DeviceId]("device")
    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def pointConstraint =
      foreignKey("gps_sentences_boat_fk", device, boatsTable)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )

    def forInserts =
      (sentence, device) <> ((GPSSentenceInput.apply _).tupled, GPSSentenceInput.unapply)
    def * =
      (id, sentence, device, added) <> ((GPSSentenceRow.apply _).tupled, GPSSentenceRow.unapply)
  }

  class GPSTable(tag: Tag) extends Table[GPSPointRow](tag, "gps_points") {
    def id = column[GPSPointId]("id", O.PrimaryKey, O.AutoInc)
    def lon = column[Longitude]("longitude")
    def lat = column[Latitude]("latitude")
    def coord = column[Coord]("coord")
    def satellites = column[Int]("satellites")
    def fix = column[GPSFix]("fix")
    def device = column[DeviceId]("device")
    def pointIndex = column[Int]("point_index", O.Default(0))
    def pointIndexIdx =
      index("gps_points_point_index_idx", pointIndex, unique = false)
    def gpsTime = column[Instant]("gps_time", O.SqlType(CreatedTimestampType))
    def deviceTimeIdx =
      index("gps_points_device_gps_time_idx", (device, gpsTime))
    def diff = column[DistanceM]("diff", O.Default(DistanceM.zero))
    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def deviceConstraint =
      foreignKey("gps_points_device_fk", device, boatsTable)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )

    def forInserts =
      (lon, lat, coord, satellites, fix, gpsTime, diff, device, pointIndex) <> ((GPSPointInput.apply _).tupled, GPSPointInput.unapply)

    def * =
      (
        id,
        lon,
        lat,
        coord,
        satellites,
        fix,
        pointIndex,
        gpsTime,
        diff,
        device,
        added
      ) <> ((GPSPointRow.apply _).tupled, GPSPointRow.unapply)
  }

  class GPSSentencesPointsLink(tag: Tag)
      extends Table[GPSSentencePointLink](tag, "gps_sentence_points") {
    def sentence = column[GPSSentenceKey]("sentence")
    def point = column[GPSPointId]("point")

    def pKey = primaryKey("gps_sentence_points_pk", (sentence, point))

    def sentenceConstraint =
      foreignKey(
        "gps_sentence_points_sentence_fk",
        sentence,
        gpsSentencesTable
      )(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )

    def pointConstraint =
      foreignKey("gps_sentence_points_point_fk", point, gpsPointsTable)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )

    def * =
      (sentence, point) <> ((GPSSentencePointLink.apply _).tupled, GPSSentencePointLink.unapply)
  }

  class TrackPointsTable(tag: Tag) extends Table[TrackPointRow](tag, "points") {
    def id = column[TrackPointId]("id", O.PrimaryKey, O.AutoInc)
    def lon = column[Longitude]("longitude")
    def lat = column[Latitude]("latitude")
    def coord = column[Coord]("coord")
    def boatSpeed = column[SpeedM]("boat_speed")
    def speedIdx = index("points_track_speed_idx", (track, boatSpeed))
    def waterTemp = column[Temperature]("water_temp")
    def tempIdx = index("points_track_water_temp_idx", (track, waterTemp))
    def depth = column[DistanceM]("depthm")
    def depthIdx = index("points_track_depth_idx", (track, depth))
    def depthOffset = column[DistanceM]("depth_offsetm")
    def boatTime = column[Instant]("boat_time", O.SqlType(CreatedTimestampType))
    def timeIdx = index("points_track_boat_time_idx", (track, boatTime))
    def track = column[TrackId]("track")
    def trackIndex = column[Int]("track_index", O.Default(0))
    def trackIndexIdx =
      index("points_track_index_idx", trackIndex, unique = false)
    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))
    def diff = column[DistanceM]("diff", O.Default(DistanceM.zero))

    def trackConstraint = foreignKey("points2_track_fk", track, tracksTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )
    def diffIdx = index("points_track_diff_idx", (track, diff))
    // I think this is the best index for aggregate calculations (group by with avg, sum, max, min)
    def allIdx =
      index(
        "points_track_all_idx",
        (track, boatTime, boatSpeed, waterTemp, diff)
      )

    def forInserts =
      (
        lon,
        lat,
        coord,
        boatSpeed,
        waterTemp,
        depth,
        depthOffset,
        boatTime,
        track,
        trackIndex,
        diff
      ) <> ((TrackPointInput.apply _).tupled, TrackPointInput.unapply)
    def combined =
      LiftedCoord(
        id,
        lon,
        lat,
        coord,
        boatSpeed,
        waterTemp,
        depth,
        depthOffset,
        boatTime,
        dateOf(boatTime),
        track,
        added
      )
    def * =
      (
        id,
        lon,
        lat,
        coord,
        boatSpeed,
        waterTemp,
        depth,
        depthOffset,
        boatTime,
        track,
        trackIndex,
        diff,
        added
      ) <> ((TrackPointRow.apply _).tupled, TrackPointRow.unapply)
  }

  class SentencesPointsLink(tag: Tag)
      extends Table[SentencePointLink](tag, "sentence_points") {
    def sentence = column[SentenceKey]("sentence")
    def point = column[TrackPointId]("point")

    def pKey = primaryKey("sentence_points_pk", (sentence, point))

    def sentenceConstraint =
      foreignKey("sentence_points_sentence_fk", sentence, sentencesTable)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )

    def pointConstraint =
      foreignKey("sentence_points_point_fk", point, pointsTable)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )

    def * =
      (sentence, point) <> ((SentencePointLink.apply _).tupled, SentencePointLink.unapply)
  }

  class TracksTable(tag: Tag) extends Table[TrackRow](tag, "tracks") {
    def id = column[TrackId]("id", O.PrimaryKey, O.AutoInc)
    def name = column[TrackName]("name", O.Length(128))
    def boat = column[DeviceId]("boat")
    def avgSpeed = column[Option[SpeedM]]("avg_speed")
    def avgWaterTemp = column[Option[Temperature]]("avg_water_temp")
    def points = column[Int]("points")
    def distance = column[DistanceM]("distance")
    def title = column[Option[TrackTitle]]("title", O.Length(191), O.Unique)
    def canonical = column[TrackCanonical]("canonical", O.Length(191), O.Unique)
    def comments = column[Option[String]]("comments")
    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def boatConstraint = foreignKey("tracks_boat_fk", boat, boatsTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )

    def forInserts =
      (name, boat, avgSpeed, avgWaterTemp, points, distance, canonical) <> ((TrackInput.apply _).tupled, TrackInput.unapply)

    def * =
      (
        id,
        name,
        boat,
        avgSpeed,
        avgWaterTemp,
        points,
        distance,
        title,
        canonical,
        comments,
        added
      ) <> ((TrackRow.apply _).tupled, TrackRow.unapply)
  }

  class BoatsTable(tag: Tag) extends Table[BoatRow](tag, "boats") {
    def id = column[DeviceId]("id", O.PrimaryKey, O.AutoInc)
    def name = column[BoatName]("name", O.Unique, O.Length(128))
    def token = column[BoatToken]("token", O.Unique, O.Length(128))
    def owner = column[UserId]("owner")
    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def userConstraint = foreignKey("boats_owner_fk", owner, usersTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )

    def forInserts =
      (name, token, owner) <> ((BoatInput.apply _).tupled, BoatInput.unapply)

    def * =
      (id, name, token, owner, added) <> ((BoatRow.apply _).tupled, BoatRow.unapply)
  }

  class UsersTable(tag: Tag) extends Table[UserRow](tag, "users") {
    def id = column[UserId]("id", O.PrimaryKey, O.AutoInc)
    def user = column[Username]("user", O.Unique, O.Length(128))
    def email = column[Option[Email]]("email", O.Unique, O.Length(128))
    def token = column[UserToken]("token", O.Length(128), O.Unique)
    def language =
      column[Language]("language", O.Length(64), O.Default(Language.default))
    def enabled = column[Boolean]("enabled")
    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def forInserts =
      (user, email, token, enabled) <> ((NewUser.apply _).tupled, NewUser.unapply)

    def * =
      (id, user, email, token, language, enabled, added) <> ((UserRow.apply _).tupled, UserRow.unapply)
  }

  def first[T, R](q: Query[T, R, Seq], onNotFound: => String)(
    implicit ec: ExecutionContext
  ): DBIOAction[R, NoStream, Effect.Read with Effect] =
    q.result.headOption.flatMap { maybeRow =>
      maybeRow.map(DBIO.successful).getOrElse {
        log.warn(onNotFound)
        DBIO.failed(new NotFoundException(onNotFound))
      }
    }
}
