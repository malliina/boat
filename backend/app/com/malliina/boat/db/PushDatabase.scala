package com.malliina.boat.db

import com.malliina.boat.db.PushDatabase.log
import com.malliina.boat.push._
import com.malliina.boat.{PushId, PushToken, TrackMeta}
import com.malliina.values.UserId
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object PushDatabase {
  val log = Logger(getClass)

  def apply(db: PushSchema, push: PushEndpoint, ec: ExecutionContext): PushDatabase =
    new PushDatabase(db, push)(ec)
}

class PushDatabase(db: PushSchema, val push: PushEndpoint)(implicit ec: ExecutionContext)  {
  import db._
  import db.api._

  def enable(input: PushInput): Future[PushId] = db.action {
    (pushInserts += input).map { id =>
      log.info(s"Enabled notifications for ${input.device} token '${input.token}'.")
      id
    }
  }

  def disable(token: PushToken, user: UserId): Future[Boolean] = db.action {
    pushTable.filter(p => p.token === token && p.user === user).delete.map { rows =>
      if (rows > 0) {
        log.info(s"Disabled notifications for token '$token'.")
        true
      } else {
        log.warn(s"Tried to disable notifications for '$token', but no changes were made.")
        false
      }
    }
  }

  def push(boat: TrackMeta, state: BoatState): Future[Unit] = {
    val notification = BoatNotification(boat.boatName, state)
    val eligibleTokens = action {
      pushTable.filter(t => t.user === boat.user).result
    }
    for {
      tokens <- eligibleTokens
      results <- Future.traverse(tokens)(token => push.push(notification, token))
      _ <- handle(results.fold(PushSummary.empty)(_ ++ _))
    } yield ()
  }

  /** Maintains tokens based on the `summary` which is available after a push request.
    *
    * Removes bad tokens and updates updated tokens.
    *
    * @return number of changed tokens
    */
  private def handle(summary: PushSummary): Future[Int] =
    if (summary.isEmpty) {
      Future.successful(0)
    } else {
      val deleteAction =
        pushTable.filter(t => t.token.inSet(summary.badTokens)).delete.map { deleted =>
          if (deleted > 0) {
            log.info(s"Removed $deleted bad tokens: ${summary.badTokens.mkString(", ")}")
          }
          deleted
        }
      val updateAction = DBIO.sequence(summary.replacements.map { repl =>
        pushTable
          .filter(d => d.token === repl.oldToken && d.device === repl.device)
          .map(_.token)
          .update(repl.newToken).map { updated =>
          if (updated > 0) {
            log.info(s"Updated token to '${repl.newToken}' from '${repl.oldToken}'.")
          }
          updated
        }
      }).map(_.sum)
      action {
        DBIO.sequence(Seq(deleteAction, updateAction)).map(_.sum)
      }
    }
}
