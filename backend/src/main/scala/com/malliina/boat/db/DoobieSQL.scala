package com.malliina.boat.db

import cats.effect.kernel.Sync
import cats.implicits.*
import com.malliina.boat.{Coord, DeviceId}
import com.malliina.measure.DistanceM
import doobie.ConnectionIO
import doobie.implicits.*

trait DoobieSQL:
  export DoobieMappings.*
  def boatById(id: DeviceId): ConnectionIO[BoatRow] =
    sql"select id, name, token, owner, added from boats b where b.id = $id".query[BoatRow].unique
  def computeDistance(from: Coord, to: Coord): ConnectionIO[DistanceM] =
    sql"select st_distance_sphere($from, $to)".query[DistanceM].unique
  protected def pure[A](a: A): ConnectionIO[A] = a.pure[ConnectionIO]
  protected def fail[A](e: Exception): ConnectionIO[A] = Sync[ConnectionIO].raiseError(e)
