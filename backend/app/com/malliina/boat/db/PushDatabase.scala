package com.malliina.boat.db

import com.malliina.boat.db.PushDatabase.log
import com.malliina.boat.push.{BoatNotification, BoatState, PushSystem}
import com.malliina.boat.{MobileDevice, PushId, PushToken, TrackMeta}
import com.malliina.push.apns.APNSToken
import com.malliina.values.UserId
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object PushDatabase {
  val log = Logger(getClass)

  def apply(db: BoatSchema, push: PushSystem, ec: ExecutionContext): PushDatabase =
    new PushDatabase(db, push)(ec)
}

class PushDatabase(val db: BoatSchema, val push: PushSystem)(implicit ec: ExecutionContext) extends DatabaseOps(db) {

  import db._
  import db.api._

  def enable(input: PushInput): Future[PushId] = action {
    (pushInserts += input).map { id =>
      log.info(s"Enabled notifications for ${input.device} token '${input.token}'.")
      id
    }
  }

  def disable(token: PushToken, user: UserId): Future[Boolean] = action {
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
      pushTable.filter(t => t.user === boat.user && t.device === MobileDevice.ios).result
    }
    for {
      tokens <- eligibleTokens
      _ <- Future.traverse(tokens.map(_.token))(token => push.push(notification, APNSToken(token.token)))
    } yield ()

  }
}
