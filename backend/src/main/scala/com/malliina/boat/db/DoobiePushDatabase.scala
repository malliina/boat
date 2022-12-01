package com.malliina.boat.db

import cats.effect.{Async, IO, Sync}
import cats.data.NonEmptyList
import cats.implicits.*
import com.malliina.boat.db.DoobiePushDatabase.log
import com.malliina.boat.push.*
import com.malliina.boat.{PushId, PushToken, UserDevice}
import com.malliina.util.AppLogger
import com.malliina.values.UserId
import doobie.{Fragments, LogHandler}
import doobie.implicits.*

object DoobiePushDatabase:
  private val log = AppLogger(getClass)

class DoobiePushDatabase[F[_]: Async](db: DoobieDatabase[F], push: PushEndpoint[F])
  extends PushService[F]
  with DoobieSQL:
  implicit val logger: LogHandler = db.logHandler
  val F = Sync[F]

  def enable(input: PushInput): F[PushId] = db.run {
    val existing = sql"""select id 
                         from push_clients 
                         where token = ${input.token} and device = ${input.device}"""
      .query[PushId]
      .option
    existing.flatMap { idOpt =>
      idOpt.map { id =>
        log.info(s"${input.device} token ${input.token} already registered for push notifications.")
        pure(id)
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

  def disable(token: PushToken, user: UserId): F[Boolean] = db.run {
    sql"delete from push_clients where token = $token and user = $user".update.run.map { rows =>
      if rows > 0 then
        log.info(s"Disabled notifications for token '$token'.")
        true
      else
        log.warn(s"Tried to disable notifications for '$token', but no changes were made.")
        false
    }
  }

  def push(device: UserDevice, state: BoatState): F[PushSummary] =
    val notification = BoatNotification(device.deviceName, state)
    val devices = db.run {
      sql"select id, token, device, user, added from push_clients where user = ${device.userId}"
        .query[PushDevice]
        .to[List]
    }
    for
      tokens <- devices
      results <- tokens.traverse(token => push.push(notification, token))
      summary = results.fold(PushSummary.empty)(_ ++ _)
      _ <- handle(summary)
    yield summary

  private def handle(summary: PushSummary): F[Int] =
    log.info(summary.describe)
    if summary.noBadTokensOrReplacements then F.pure(0)
    else
      db.run {
        val deleteIO = summary.badTokens.toList.toNel.map { bad =>
          val inClause = Fragments.in(fr"token", bad)
          sql"delete from push_clients where $inClause".update.run.map { deleted =>
            if deleted > 0 then
              log.info(s"Removed $deleted bad tokens: ${summary.badTokens.mkString(", ")}")
            deleted
          }
        }.getOrElse {
          pure(0)
        }
        val updateIO = summary.replacements.toList.traverse { repl =>
          sql"update push_clients set token = ${repl.newToken} where token = ${repl.oldToken} and device = ${repl.device}".update.run.map {
            updated =>
              if updated > 0 then
                log.info(s"Updated token to '${repl.newToken}' from '${repl.oldToken}'.")
              updated
          }
        }.map(_.sum)
        deleteIO.flatMap { r1 => updateIO.map { r2 => r1 + r2 } }
      }
