package com.malliina.boat.db

import cats.effect.Async
import cats.implicits.*
import com.malliina.boat.db.BoatVesselDatabase.{collect, log}
import com.malliina.boat.db.Values.{RowsChanged, VesselUpdateId}
import com.malliina.boat.http.VesselQuery
import com.malliina.boat.{Mmsi, VesselInfo, VesselRowId}
import com.malliina.database.DoobieDatabase
import com.malliina.util.AppLogger
import doobie.*
import doobie.free.preparedstatement.PreparedStatementIO
import doobie.implicits.*

import scala.annotation.unused

trait VesselDatabase[F[_]]:
  def load(query: VesselQuery): F[List[VesselHistory]]
  def save(messages: Seq[VesselInfo]): F[List[VesselUpdateId]]

object BoatVesselDatabase:
  private val log = AppLogger(getClass)

  def collect(rows: List[VesselRow]): List[VesselHistory] =
    rows
      .foldLeft(Vector.empty[VesselHistory]): (acc, row) =>
        val idx = acc.indexWhere(_.mmsi == row.mmsi)
        val entry = VesselUpdate.from(row)
        if idx >= 0 then
          val old = acc(idx)
          acc.updated(idx, old.copy(updates = old.updates :+ entry))
        else acc :+ VesselHistory(row.mmsi, row.name, row.draft, List(entry))
      .toList

class BoatVesselDatabase[F[_]: Async](db: DoobieDatabase[F])
  extends VesselDatabase[F]
  with DoobieSQL:
  override def load(query: VesselQuery): F[List[VesselHistory]] = db.run:
    val time = query.time
    val limits = query.limits
    val conditions = Fragments.whereAndOpt(
      query.names.toList.toNel.map(ns => Fragments.in(fr"v.name", ns)),
      query.mmsis.toList.toNel.map(ms => Fragments.in(fr"v.mmsi", ms)),
      time.from.map(f => fr"u.added >= $f"),
      time.to.map(t => fr"u.added <= $t")
    )
    val start = System.currentTimeMillis()
    sql"""select u.id, v.mmsi, v.name, u.coord, u.sog, u.cog, v.draft, u.destination, u.heading, u.eta, u.added
          from mmsis v join mmsi_updates u on v.mmsi = u.mmsi $conditions
          order by u.added desc
          limit ${limits.limit} offset ${limits.offset}"""
      .query[VesselRow]
      .to[List]
      .map: rows =>
        val durationMs = System.currentTimeMillis() - start
        log.info(
          s"Searched for vessels with ${query.describe}. Got ${rows.length} rows in $durationMs ms."
        )
        collect(rows)

  def save(messages: Seq[VesselInfo]): F[List[VesselUpdateId]] =
    val io = for
      rowCount <- saveVessels(messages)
      ids <- saveUpdates(messages)
    yield ids
    db.run(io)

  private def saveVessels(messages: Seq[VesselInfo]): ConnectionIO[RowsChanged] =
    val mmsis = messages.distinctBy(msg => msg.mmsi).map(v => MmsiRow(v.mmsi, v.name, v.draft))
    val sql = s"insert ignore into mmsis(mmsi, name, draft) values(?, ?, ?)"
    Update[MmsiRow](sql).updateMany(mmsis).map(rows => RowsChanged(rows))

  private def saveUpdates(messages: Seq[VesselInfo]): ConnectionIO[List[VesselUpdateId]] =
    val sql =
      s"insert into mmsi_updates(mmsi, coord, sog, cog, destination, heading, eta, vessel_time) values(?, ?, ?, ?, ?, ?, ?, ?)"
    val rows = messages.map { msg =>
      MmsiUpdateRow(
        msg.mmsi,
        msg.coord,
        msg.sog,
        msg.cog,
        msg.destination,
        msg.heading,
        msg.eta,
        msg.timestampMillis
      )
    }
    Update[MmsiUpdateRow](sql)
      .updateManyWithGeneratedKeys[VesselUpdateId]("id")(rows)
      .compile
      .toList

  @unused
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
