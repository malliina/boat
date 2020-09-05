package com.malliina.boat.db

import cats.implicits._
import com.malliina.boat.db.DoobiePushDatabase.log
import com.malliina.boat.http.BoatQuery
import com.malliina.boat.push.{BoatNotification, BoatState, PushEndpoint, PushService, PushSummary}
import com.malliina.boat.{PushId, PushToken, UserDevice}
import com.malliina.values.UserId
import doobie.Fragments
import doobie.implicits._
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object DoobiePushDatabase {
  private val log = Logger(getClass)

  def apply(db: DoobieDatabase, push: PushEndpoint): DoobiePushDatabase =
    new DoobiePushDatabase(db, push)
}

class DoobiePushDatabase(db: DoobieDatabase, push: PushEndpoint)
  extends PushService
  with DoobieSQL {
  import DoobieMappings._
  implicit val ec: ExecutionContext = db.ec

  def enable(input: PushInput): Future[PushId] = db.run {
    sql"""insert into push_clients(token, device, user) 
          values(${input.token}, ${input.device}, ${input.user})""".update
      .withUniqueGeneratedKeys[PushId]("id")
      .map { id =>
        log.info(s"Enabled notifications for ${input.device} token '${input.token}'.")
        id
      }
  }

  def disable(token: PushToken, user: UserId): Future[Boolean] = db.run {
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

  def push(device: UserDevice, state: BoatState): Future[PushSummary] = {
    val notification = BoatNotification(device.deviceName, state)
    val devices = db.run {
      sql"select id, token, device, user, added from push_clients where user = ${device.userId}"
        .query[PushDevice]
        .to[List]
    }
    for {
      tokens <- devices
      results <- Future.traverse(tokens) { token => push.push(notification, token) }
      summary = results.fold(PushSummary.empty)(_ ++ _)
      _ <- handle(summary)
    } yield summary
  }

  private def handle(summary: PushSummary): Future[Int] =
    if (summary.isEmpty) {
      Future.successful(0)
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
