package com.malliina.boat.db

import cats.effect.kernel.Sync
import cats.syntax.all.catsSyntaxApplicativeId
import com.malliina.boat.{Coord, DeviceId}
import com.malliina.measure.DistanceM
import doobie.ConnectionIO
import doobie.implicits.*

trait DoobieSQL:
  export Mappings.given
  export doobie.implicits.toSqlInterpolator
  def boatById(id: DeviceId): ConnectionIO[SourceRow] =
    sql"select ${SourceRow.columns} from boats b where b.id = $id"
      .query[SourceRow]
      .unique
  def computeDistance(from: Coord, to: Coord): ConnectionIO[DistanceM] =
    sql"select st_distance_sphere($from, $to)".query[DistanceM].unique
  protected def pure[A](a: A): ConnectionIO[A] = a.pure[ConnectionIO]
  protected def fail[A](e: Exception): ConnectionIO[A] = Sync[ConnectionIO].raiseError(e)
