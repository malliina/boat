package com.malliina.boat.db

import cats.data.NonEmptyList
import cats.effect.Async
import cats.implicits.*
import com.malliina.boat.*
import com.malliina.boat.db.CarDatabase.log
import com.malliina.boat.http.CarQuery
import com.malliina.measure.DistanceM
import com.malliina.util.AppLogger
import com.malliina.values.UserId
import doobie.*
import doobie.implicits.*

object CarDatabase:
  private val log = AppLogger(getClass)

class CarDatabase[F[_]: Async](val db: DoobieDatabase[F]) extends DoobieSQL:
  private val maxTimeBetweenCarUpdates = Constants.MaxTimeBetweenCarUpdates
  import db.{logHandler, run}

  def save(locs: LocationUpdates, userInfo: MinimalUserInfo, user: UserId): F[List[CarRow]] =
    saveToDatabase(locs, userInfo, user)

  private def saveToDatabase(
    locs: LocationUpdates,
    userInfo: MinimalUserInfo,
    user: UserId
  ): F[List[CarRow]] =
    run {
      val carId = locs.carId
      val ownershipCheck =
        sql"select exists(select b.id from boats b where b.id = $carId and b.owner = $user)"
          .query[Boolean]
          .unique
      import cats.implicits.*
      val insertion = locs.updates.traverse { loc =>
        for
          prev <-
            sql"""select p.coord
                  from car_points p
                  where p.device = ${locs.carId} and timestampdiff(SECOND, p.gps_time, ${loc.date}) < $maxTimeBetweenCarUpdates
                  order by p.added desc limit 1"""
              .query[Coord]
              .option
          diff <- prev
            .map(p => computeDistance(p, loc.coord))
            .getOrElse(pure(DistanceM.zero))
          insertion <-
            sql"""insert into car_points(longitude, latitude, coord, gps_time, diff, device, altitude, accuracy, bearing, bearing_accuracy, speed, battery, capacity, car_range, outside_temperature, night_mode)
                  values(${loc.longitude}, ${loc.latitude}, ${loc.coord}, ${loc.date}, $diff, $carId, ${loc.altitudeMeters}, ${loc.accuracyMeters}, ${loc.bearing}, ${loc.bearingAccuracyDegrees},
                    ${loc.speed},
                    ${loc.batteryLevel},
                    ${loc.batteryCapacity},
                    ${loc.rangeRemaining},
                    ${loc.outsideTemperature},
                    ${loc.nightMode})
           """.update.withUniqueGeneratedKeys[CarUpdateId]("id")
        yield insertion
      }
      for
        exists <- ownershipCheck
        _ <- if exists then pure(()) else fail(BoatNotFoundException(carId, user))
        ids <- insertion
        inserted <- if ids.isEmpty then pure(Nil) else historyQuery(CarQuery.ids(ids), userInfo)
      yield
        if ids.nonEmpty then log.debug(s"Inserted to car $carId IDs ${ids.mkString(", ")}.")
        inserted
    }

  private def historyQuery(filters: CarQuery, user: MinimalUserInfo) =
    val time = filters.timeRange
    val limits = filters.limits
    val conditions = Fragments.whereAndOpt(
      time.from.map(f => fr"c.added >= $f"),
      time.to.map(t => fr"c.added <= $t"),
      filters.ids.toNel.map(ids => Fragments.in(fr"c.id", ids)),
      Option(fr"u.user = ${user.username}")
    )
    sql"""select c.coord, c.diff, c.speed, c.altitude, c.battery, c.capacity, c.car_range, c.outside_temperature, c.night_mode, c.gps_time, c.added, b.id, b.name, u.user
          from car_points c
          join boats b on b.id = c.device
          join users u on b.owner = u.id
          $conditions
          order by c.added desc
          limit ${limits.limit} offset ${limits.offset}"""
      .query[CarRow]
      .to[List]
