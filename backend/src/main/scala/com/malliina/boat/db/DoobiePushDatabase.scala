package com.malliina.boat.db

import cats.effect.IO
import cats.implicits._
import com.malliina.boat.db.DoobieMappings._
import com.malliina.boat.db.DoobiePushDatabase.log
import com.malliina.boat.http.BoatQuery
import com.malliina.boat.push._
import com.malliina.boat.{PushId, PushToken, UserDevice}
import com.malliina.util.AppLogger
import com.malliina.values.UserId
import doobie.Fragments
import doobie.implicits._

object DoobiePushDatabase {
  private val log = AppLogger(getClass)

  def apply(db: DoobieDatabase, push: PushEndpoint): DoobiePushDatabase =
    new DoobiePushDatabase(db, push)
}

class DoobiePushDatabase(db: DoobieDatabase, push: PushEndpoint)
  extends PushService
  with DoobieSQL {

  def enable(input: PushInput): IO[PushId] = db.run {
    val existing = sql"""select id 
                         from push_clients 
                         where token = ${input.token} and device = ${input.device}"""
      .query[PushId]
      .option
    existing.flatMap { idOpt =>
      idOpt.map { id =>
        log.info(s"${input.device} token ${input.token} already registered for push notifications.")
        AsyncConnectionIO.pure(id)
      }.getOrElse {
        sql"""insert into push_clients(token, device, user) 
              values(${input.token}, ${input.device}, ${input.user})""".update
          .withUniqueGeneratedKeys[PushId]("id")
          .map { id =>
            log.info(s"Enabled notifications for ${input.device} token '${input.token}'.")
            id
          }
      }
    }
  }

  def disable(token: PushToken, user: UserId): IO[Boolean] = db.run {
    sql"delete from push_clients where token = $token and user = $user".update.run.map { rows =>
      if (rows > 0) {
        log.info(s"Disabled notifications for token '$token'.")
        true
      } else {
        log.warn(s"Tried to disable notifications for '$token', but no changes were made.")
        false
      }
    }
  }

  def push(device: UserDevice, state: BoatState): IO[PushSummary] = {
    val notification = BoatNotification(device.deviceName, state)
    val devices = db.run {
      sql"select id, token, device, user, added from push_clients where user = ${device.userId}"
        .query[PushDevice]
        .to[List]
    }
    for {
      tokens <- devices
      results <- tokens.traverse(token => push.push(notification, token))
      summary = results.fold(PushSummary.empty)(_ ++ _)
      _ <- handle(summary)
    } yield summary
  }

  private def handle(summary: PushSummary): IO[Int] =
    if (summary.isEmpty) {
      IO.pure(0)
    } else {
      db.run {
        val deleteIO = BoatQuery
          .toNonEmpty(summary.badTokens.toList)
          .map { bad =>
            val inClause = Fragments.in(fr"token", bad)
            sql"delete from push_clients where $inClause".update.run.map { deleted =>
              if (deleted > 0) {
                log.info(s"Removed $deleted bad tokens: ${summary.badTokens.mkString(", ")}")
              }
              deleted
            }
          }
          .getOrElse {
            pure(0)
          }
        val updateIO = summary.replacements.toList.traverse { repl =>
          sql"update push_clients set token = ${repl.newToken} where token = ${repl.oldToken} and device = ${repl.device}".update.run.map {
            updated =>
              if (updated > 0) {
                log.info(s"Updated token to '${repl.newToken}' from '${repl.oldToken}'.")
              }
              updated
          }
        }.map(_.sum)
        AsyncConnectionIO.map2(deleteIO, updateIO)(_ + _)
      }
    }
}
