package com.malliina.boat.push

import com.malliina.boat.db.PushDevice
import com.malliina.boat.{MobileDevice, PushToken}
import com.malliina.push.Token
import com.malliina.push.gcm.MappedGCMResponse.TokenReplacement

import scala.concurrent.Future

case class PushTokenReplacement(oldToken: PushToken, newToken: PushToken, device: MobileDevice)

object PushTokenReplacement {
  def apply(gcm: TokenReplacement): PushTokenReplacement =
    PushTokenReplacement(PushToken(gcm.oldToken.token), PushToken(gcm.newToken.token), MobileDevice.Android)
}

case class PushSummary(badTokens: Seq[PushToken], replacements: Seq[PushTokenReplacement]) {
  def isEmpty = badTokens.isEmpty && replacements.isEmpty

  def ++(other: PushSummary): PushSummary =
    PushSummary(badTokens ++ other.badTokens, replacements ++ other.replacements)
}

object PushSummary {
  val empty = PushSummary(Nil, Nil)
}

trait PushClient[T <: Token] {
  def push(notification: BoatNotification, to: T): Future[PushSummary]
}

trait PushEndpoint {
  def push(notification: BoatNotification, to: PushDevice): Future[PushSummary]
}
