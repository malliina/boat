package com.malliina.boat.db

import cats.data.NonEmptyList
import cats.effect.Async
import cats.effect.kernel.implicits.monadCancelOps_
import cats.implicits.*
import com.malliina.boat.*
import com.malliina.boat.db.CarDatabase.{collectCars, log}
import com.malliina.boat.db.TrackInserter.log
import com.malliina.boat.http.CarQuery
import com.malliina.util.AppLogger
import com.malliina.values.UserId
import doobie.*
import doobie.implicits.*
import fs2.concurrent.Topic

import scala.annotation.tailrec
import scala.concurrent.duration.DurationInt

object CarDatabase:
  private val log = AppLogger(getClass)

  private def collectCars(rows: List[CarRow], formatter: TimeFormatter) =
    rows.reverse.foldLeft(Vector.empty[CarDrive]) { (acc, cr) =>
      val elem = cr.toUpdate(formatter)
      val idx = acc.indexWhere(_.car.id == cr.car.id)
      if idx >= 0 then
        val old = acc(idx)
        acc.updated(idx, old.copy(updates = old.updates :+ elem))
      else acc :+ CarDrive(List(elem), cr.car)
    }

class CarDatabase[F[_]: Async](val db: DoobieDatabase[F], val insertions: Topic[F, CarDrive])
  extends DoobieSQL:
  import db.{logHandler, run}

  def save(locs: LocationUpdates, userInfo: MinimalUserInfo, user: UserId): F[List[CarDrive]] =
    for
      inserted <- saveToDatabase(locs, userInfo, user)
      _ <- Async[F].parTraverseN(1)(inserted)(i => insertions.publish1(i))
    yield inserted

  private def saveToDatabase(
    locs: LocationUpdates,
    userInfo: MinimalUserInfo,
    user: UserId
  ): F[List[CarDrive]] =
    run {
      val carId = locs.carId
      val ownershipCheck =
        sql"select exists(select b.id from boats b where b.id = $carId and b.owner = $user)"
          .query[Boolean]
          .unique
      import cats.implicits.*
      val insertion = locs.updates.traverse { loc =>
        sql"""insert into car_points(longitude, latitude, coord, gps_time, device, altitude, accuracy, bearing, bearing_accuracy, speed, battery, capacity, car_range, outside_temperature, night_mode)
              values(${loc.longitude}, ${loc.latitude}, ${loc.coord}, ${loc.date}, $carId, ${loc.altitudeMeters}, ${loc.accuracyMeters}, ${loc.bearing}, ${loc.bearingAccuracyDegrees},
                ${loc.speed},
                ${loc.batteryLevel},
                ${loc.batteryCapacity},
                ${loc.rangeRemaining},
                ${loc.outsideTemperature},
                ${loc.nightMode})
         """.update.withUniqueGeneratedKeys[CarUpdateId]("id")
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

  def history(filters: CarQuery, user: MinimalUserInfo): F[List[CarDrive]] = run {
    historyQuery(filters, user)
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
    val formatter = TimeFormatter.lang(user.language)
    val start = System.currentTimeMillis()
    sql"""select c.coord, c.speed, c.battery, c.capacity, c.car_range, c.outside_temperature, c.night_mode, c.gps_time, c.added, b.id, b.name, u.user
          from car_points c
          join boats b on b.id = c.device
          join users u on b.owner = u.id
          $conditions
          order by c.added desc
          limit ${limits.limit} offset ${limits.offset}"""
      .query[CarRow]
      .to[List]
      .map { rows =>
        val sqlDone = System.currentTimeMillis()
        val result = collectCars(rows, formatter).flatMap(split).toList
        val splitDone = System.currentTimeMillis()
        val total = splitDone - start
        if total > 500.millis.toMillis then
          log.info(
            s"Car query for ${user.username} ${filters.describe} rows ${rows.size}, sql ${sqlDone - start} ms, split ${splitDone - sqlDone} ms, total $total ms."
          )
        result
      }

  private val maxTimeBetweenCarUpdates = Constants.MaxTimeBetweenCarUpdates

  def split(e: CarDrive): List[CarDrive] =
    split(e.updates).map(cs => CarDrive(cs, e.car))

  @tailrec
  private def split(
    updates: List[CarUpdate],
    previous: List[List[CarUpdate]] = Nil,
    acc: List[CarUpdate] = Nil
  ): List[List[CarUpdate]] =
    updates match
      case head :: tail =>
        acc match
          case accHead :: _
              if head.carTime.millis - accHead.carTime.millis < maxTimeBetweenCarUpdates.toMillis =>
            split(tail, previous, head :: acc)
          case _ =>
            split(tail, previous :+ acc.reverse, List(head))
      case Nil =>
        previous :+ acc.reverse
