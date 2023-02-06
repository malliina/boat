package com.malliina.boat.db

import cats.effect.Async
import cats.effect.syntax.all.*
import cats.implicits.*
import com.malliina.boat.db.BoatVesselDatabase.log
import com.malliina.boat.http.{Limits, TimeRange, VesselQuery}
import com.malliina.boat.{Mmsi, VesselInfo, VesselRowId}
import com.malliina.util.AppLogger
import doobie.*
import doobie.free.preparedstatement.PreparedStatementIO
import doobie.implicits.*
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.time.OffsetDateTime

trait VesselDatabase[F[_]]:
  def load(query: VesselQuery): F[List[VesselRow]]
  def save(messages: Seq[VesselInfo]): F[List[VesselRowId]]

object BoatVesselDatabase:
  private val log = AppLogger(getClass)

class BoatVesselDatabase[F[_]: Async](db: DoobieDatabase[F])
  extends VesselDatabase[F]
  with DoobieSQL:

  override def load(query: VesselQuery): F[List[VesselRow]] = db.run {
    val time = query.time
    val limits = query.limits
    val conditions = Fragments.whereAndOpt(
      query.names.toList.toNel.map(ns => Fragments.in(fr"v.name", ns)),
      query.mmsis.toList.toNel.map(ms => Fragments.in(fr"v.mmsi", ms)),
      time.from.map(f => fr"v.added >= $f"),
      time.to.map(t => fr"v.added <= $t")
    )
    val start = System.currentTimeMillis()
    sql"""select id, mmsi, name, coord, sog, cog, draft, destination, heading, eta, added
          from vessels v $conditions
          order by v.added desc
          limit ${limits.limit} offset ${limits.offset}"""
      .query[VesselRow]
      .to[List]
      .map { rows =>
        val durationMs = System.currentTimeMillis() - start
        log.info(
          s"Searched for vessels with ${query.describe}. Got ${rows.length} rows in $durationMs ms."
        )
        rows
      }
  }

  override def save(messages: Seq[VesselInfo]): F[List[VesselRowId]] = db.run {
    val start = System.currentTimeMillis()
    if messages.nonEmpty then
      saveStream(messages).compile.toList.map { ids =>
        val durationMs = System.currentTimeMillis() - start
        log.info(s"Inserted ${ids.length} vessel rows in $durationMs ms.")
        ids
      }
    else pure(Nil)
  }

  private def saveStream(messages: Seq[VesselInfo]): fs2.Stream[ConnectionIO, VesselRowId] =
    val oneRow = (1 to 10).map(_ => "?").mkString("(", ",", ")")
    val params = messages.map(_ => oneRow).mkString(",")
    val sql =
      s"insert into vessels(mmsi, name, coord, sog, cog, draft, destination, heading, eta, vessel_time) values$params"
    val prep = messages.foldLeft((().pure[PreparedStatementIO], 1)) { case ((acc, pos), info) =>
      val newAcc =
        acc *> HPS.set(pos, info.mmsi) *>
          HPS.set(pos + 1, info.name) *>
          HPS.set(pos + 2, info.coord) *>
          HPS.set(pos + 3, info.sog) *>
          HPS.set(pos + 4, info.cog) *>
          HPS.set(pos + 5, info.draft) *>
          HPS.set(pos + 6, info.destination) *>
          HPS.set(pos + 7, info.heading) *>
          HPS.set(pos + 8, info.eta) *>
          HPS.set(pos + 9, info.timestampMillis)
      (newAcc, pos + 10)
    }
    HC.updateWithGeneratedKeys[VesselRowId](List("id"))(sql, prep._1, 512)
