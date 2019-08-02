package com.malliina.boat.db

import java.time.Instant

import com.malliina.boat.{BoatId, BoatName, BoatToken, CombinedCoord, Coord, DateVal, JoinedBoat, JoinedTrack, Language, Latitude, Longitude, MonthVal, TrackCanonical, TrackId, TrackMeta, TrackName, TrackNumbers, TrackPointId, TrackTitle, YearVal}
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.{Email, UserId, Username}

import scala.concurrent.duration.FiniteDuration

trait QueryModels { self: MappingsT with JdbcComponent =>
  import self.api._

  case class LiftedCoord(id: Rep[TrackPointId],
                         lon: Rep[Longitude],
                         lat: Rep[Latitude],
                         coord: Rep[Coord],
                         boatSpeed: Rep[SpeedM],
                         waterTemp: Rep[Temperature],
                         depth: Rep[DistanceM],
                         depthOffset: Rep[DistanceM],
                         boatTime: Rep[Instant],
                         date: Rep[DateVal],
                         track: Rep[TrackId],
                         added: Rep[Instant])

  implicit object Coordshape
    extends CaseClassShape(LiftedCoord.tupled, (CombinedCoord.apply _).tupled)

  case class LiftedJoinedBoat(boat: Rep[BoatId],
                              boatName: Rep[BoatName],
                              token: Rep[BoatToken],
                              user: Rep[UserId],
                              username: Rep[Username],
                              email: Rep[Option[Email]],
                              language: Rep[Language])

  implicit object JoinedBoatShape extends CaseClassShape(LiftedJoinedBoat.tupled, JoinedBoat.tupled)

  case class LiftedTrackMeta(track: Rep[TrackId],
                             trackName: Rep[TrackName],
                             trackTitle: Rep[Option[TrackTitle]],
                             trackCanonical: Rep[TrackCanonical],
                             comments: Rep[Option[String]],
                             trackAdded: Rep[Instant],
                             avgSpeed: Rep[Option[SpeedM]],
                             avgWaterTemp: Rep[Option[Temperature]],
                             points: Rep[Int],
                             distance: Rep[DistanceM],
                             boat: Rep[BoatId],
                             boatName: Rep[BoatName],
                             token: Rep[BoatToken],
                             user: Rep[UserId],
                             username: Rep[Username],
                             email: Rep[Option[Email]])

  implicit object LiftedTrackMetaShape
    extends CaseClassShape(LiftedTrackMeta.tupled, (TrackMeta.apply _).tupled)

  case class LiftedJoinedTrack(track: Rep[TrackId],
                               trackName: Rep[TrackName],
                               trackTitle: Rep[Option[TrackTitle]],
                               canonical: Rep[TrackCanonical],
                               comments: Rep[Option[String]],
                               trackAdded: Rep[Instant],
                               boat: LiftedJoinedBoat,
                               points: Rep[Int],
                               start: Rep[Option[Instant]],
                               startDate: Rep[DateVal],
                               startMonth: Rep[MonthVal],
                               startYear: Rep[YearVal],
                               end: Rep[Option[Instant]],
                               duration: Rep[FiniteDuration],
                               topSpeed: Rep[Option[SpeedM]],
                               avgSpeed: Rep[Option[SpeedM]],
                               avgWaterTemp: Rep[Option[Temperature]],
                               length: Rep[DistanceM],
                               topPoint: LiftedCoord) {
    def boatId = boat.boat
    def username = boat.username
    def user = boat.user
    def language = boat.language
  }

  implicit object TrackShape
    extends CaseClassShape(LiftedJoinedTrack.tupled, (JoinedTrack.apply _).tupled)

  case class LiftedTrackStats(track: Rep[TrackId],
                              start: Rep[Option[Instant]],
                              end: Rep[Option[Instant]],
                              topSpeed: Rep[Option[SpeedM]])

  implicit object TrackStatsShape
    extends CaseClassShape(LiftedTrackStats.tupled, (TrackNumbers.apply _).tupled)

}
