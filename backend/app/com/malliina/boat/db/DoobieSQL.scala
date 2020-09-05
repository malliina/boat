package com.malliina.boat.db

import com.malliina.boat.{Coord, DeviceId}
import com.malliina.measure.DistanceM
import doobie.ConnectionIO
import doobie.implicits._

trait DoobieSQL {
  import DoobieMappings._
  def boatById(id: DeviceId) =
    sql"select id, name, token, owner, added from boats b where b.id = $id".query[BoatRow].unique
  def computeDistance(from: Coord, to: Coord) =
    sql"select st_distance_sphere($from, $to)".query[DistanceM].unique
  protected def pure[A](a: A): ConnectionIO[A] = AsyncConnectionIO.pure(a)
  protected def fail[A](message: String): ConnectionIO[A] = fail(new Exception(message))
  protected def fail[A](e: Throwable): ConnectionIO[A] = AsyncConnectionIO.raiseError(e)
}
