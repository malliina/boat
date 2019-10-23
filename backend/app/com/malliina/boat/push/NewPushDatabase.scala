package com.malliina.boat.push

import com.malliina.boat.db.{BoatDatabase, PushDevice, PushInput}
import com.malliina.boat.push.NewPushDatabase.log
import com.malliina.boat.{PushId, PushToken, UserDevice}
import com.malliina.values.UserId
import io.getquill.SnakeCase
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object NewPushDatabase {
  private val log = Logger(getClass)

  def apply(db: BoatDatabase[SnakeCase], push: PushEndpoint): NewPushDatabase =
    new NewPushDatabase(db, push)(db.ec)
}

class NewPushDatabase(val db: BoatDatabase[SnakeCase], val push: PushEndpoint)(
    implicit ec: ExecutionContext
) extends PushService {
  import db._
  val pushTable = quote(querySchema[PushDevice]("push_clients"))

  def enable(input: PushInput): Future[PushId] = performAsync("Enable notifications") {
    runIO(
      pushTable
        .insert(
          _.token -> lift(input.token),
          _.device -> lift(input.device),
          _.user -> lift(input.user)
        )
        .returningGenerated(_.id)
    ).map { id =>
      log.info(s"Enabled notifications for ${input.device} token '${input.token}'.")
      id
    }
  }

  def disable(token: PushToken, user: UserId): Future[Boolean] =
    performAsync("Disable notifications") {
      runIO(pushTable.filter(p => p.token == lift(token) && p.user == lift(user)).delete).map {
        rows =>
          if (rows > 0) {
            log.info(s"Disabled notifications for token '$token'.")
            true
          } else {
            log.warn(s"Tried to disable notifications for '$token', but no changes were made.")
            false
          }
      }
    }
  def push(device: UserDevice, state: BoatState): Future[Unit] = {
    val notification = BoatNotification(device.deviceName, state)
    for {
      tokens <- performAsync("Load tokens")(runIO(pushTable.filter(_.user == lift(device.userId))))
      results <- Future.traverse(tokens: Seq[PushDevice])(token => push.push(notification, token))
      _ <- handle(results.fold(PushSummary.empty)(_ ++ _))
    } yield ()
  }

  private def handle(summary: PushSummary): Future[Int] =
    if (summary.isEmpty) {
      Future.successful(0)
    } else {
      val deleteTask: IO[Int, Effect.Write] =
        runIO(pushTable.filter(t => liftQuery(summary.badTokens).contains(t.token)).delete).map {
          deleted =>
            if (deleted > 0) {
              log.info(s"Removed $deleted bad tokens: ${summary.badTokens.mkString(", ")}")
            }
            deleted.toInt
        }
      val updateAction: IO[Int, Effect.Write] = IO
        .traverse(summary.replacements) { repl =>
          runIO(
            pushTable
              .filter(d => d.token == lift(repl.oldToken) && d.device == lift(repl.device))
              .update(_.token -> lift(repl.newToken))
          ).map { updated =>
            if (updated > 0) {
              log.info(s"Updated token to '${repl.newToken}' from '${repl.oldToken}'.")
            }
            updated.toInt
          }
        }
        .map(_.sum)
      performAsync("Manage bad tokens")(IO.sequence(List(deleteTask, updateAction)).map(_.sum))
    }
}
