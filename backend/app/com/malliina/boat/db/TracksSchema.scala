package com.malliina.boat.db

import java.time.Instant

import com.malliina.boat._
import com.malliina.boat.db.BoatSchema.CreatedTimestampType
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.{Email, UserId, Username}

trait TracksSchema extends MappingsT with DatabaseClient { self: JdbcComponent with QueryModels =>
  import api._

  val usersTable = TableQuery[UsersTable]
  val boatsTable = TableQuery[BoatsTable]
  val tracksTable = TableQuery[TracksTable]
  val sentencesTable = TableQuery[SentencesTable]
  val pointsTable = TableQuery[TrackPointsTable]
  val sentencePointsTable = TableQuery[SentencesPointsLink]

  val sentenceInserts = sentencesTable.map(_.forInserts).returning(sentencesTable.map(_.id))
  val boatInserts = boatsTable.map(_.forInserts).returning(boatsTable.map(_.id))
  val userInserts = usersTable.map(_.forInserts).returning(usersTable.map(_.id))
  val trackInserts = tracksTable.map(_.forInserts).returning(tracksTable.map(_.id))
  val coordInserts = pointsTable.map(_.forInserts).returning(pointsTable.map(_.id))

  val dateOf: Rep[Instant] => Rep[DateVal] =
    SimpleFunction.unary[Instant, DateVal](jdbc.date)

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

    def forInserts = (sentence, track) <> ((SentenceInput.apply _).tupled, SentenceInput.unapply)

    def * = (id, sentence, track, added) <> ((SentenceRow.apply _).tupled, SentenceRow.unapply)
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
    def trackIndexIdx = index("points_track_index_idx", trackIndex, unique = false)
    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))
    def diff = column[DistanceM]("diff", O.Default(DistanceM.zero))

    def trackConstraint = foreignKey("points2_track_fk", track, tracksTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )
    def diffIdx = index("points_track_diff_idx", (track, diff))
    // I think this is the best index for aggregate calculations (group by with avg, sum, max, min)
    def allIdx = index("points_track_all_idx", (track, boatTime, boatSpeed, waterTemp, diff))

    def forInserts = (lon,
                      lat,
                      coord,
                      boatSpeed,
                      waterTemp,
                      depth,
                      depthOffset,
                      boatTime,
                      track,
                      trackIndex,
                      diff) <> ((TrackPointInput.apply _).tupled, TrackPointInput.unapply)
    def combined = LiftedCoord(id,
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
                               added)
    def * = (id,
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
             added) <> ((TrackPointRow.apply _).tupled, TrackPointRow.unapply)
  }

  class SentencesPointsLink(tag: Tag) extends Table[SentencePointLink](tag, "sentence_points") {
    def sentence = column[SentenceKey]("sentence")
    def point = column[TrackPointId]("point")

    def pKey = primaryKey("sentence_points_pk", (sentence, point))

    def sentenceConstraint = foreignKey("sentence_points_sentence_fk", sentence, sentencesTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade)

    def pointConstraint = foreignKey("sentence_points_point_fk", point, pointsTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade)

    def * = (sentence, point) <> ((SentencePointLink.apply _).tupled, SentencePointLink.unapply)
  }

  class TracksTable(tag: Tag) extends Table[TrackRow](tag, "tracks") {
    def id = column[TrackId]("id", O.PrimaryKey, O.AutoInc)
    def name = column[TrackName]("name", O.Length(128))
    def boat = column[BoatId]("boat")
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

    def * = (id,
             name,
             boat,
             avgSpeed,
             avgWaterTemp,
             points,
             distance,
             title,
             canonical,
             comments,
             added) <> ((TrackRow.apply _).tupled, TrackRow.unapply)
  }

  class BoatsTable(tag: Tag) extends Table[BoatRow](tag, "boats") {
    def id = column[BoatId]("id", O.PrimaryKey, O.AutoInc)
    def name = column[BoatName]("name", O.Unique, O.Length(128))
    def token = column[BoatToken]("token", O.Unique, O.Length(128))
    def owner = column[UserId]("owner")
    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def userConstraint = foreignKey("boats_owner_fk", owner, usersTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )

    def forInserts = (name, token, owner) <> ((BoatInput.apply _).tupled, BoatInput.unapply)

    def * = (id, name, token, owner, added) <> ((BoatRow.apply _).tupled, BoatRow.unapply)
  }

  class UsersTable(tag: Tag) extends Table[DataUser](tag, "users") {
    def id = column[UserId]("id", O.PrimaryKey, O.AutoInc)
    def user = column[Username]("user", O.Unique, O.Length(128))
    def email = column[Option[Email]]("email", O.Unique, O.Length(128))
    def passHash = column[String]("pass_hash", O.Length(512))
    def token = column[UserToken]("token", O.Length(128), O.Unique)
    def language = column[Language]("language", O.Length(64), O.Default(Language.default))
    def enabled = column[Boolean]("enabled")
    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def forInserts = (user, email, token, enabled) <> ((NewUser.apply _).tupled, NewUser.unapply)

    def * =
      (id, user, email, token, language, enabled, added) <> ((DataUser.apply _).tupled, DataUser.unapply)
  }
}
