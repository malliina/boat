package com.malliina.boat.push

import cats.effect.IO
import com.malliina.boat.db.PushDevice
import com.malliina.boat.{MobileDevice, PushToken}
import com.malliina.push.Token
import com.malliina.push.gcm.MappedGCMResponse.TokenReplacement
import com.malliina.util.FileUtils

case class PushTokenReplacement(oldToken: PushToken, newToken: PushToken, device: MobileDevice)

object PushTokenReplacement:
  def apply(gcm: TokenReplacement): PushTokenReplacement =
    PushTokenReplacement(
      PushToken(gcm.oldToken.token),
      PushToken(gcm.newToken.token),
      MobileDevice.Android
    )

case class PushSummary(badTokens: Seq[PushToken], replacements: Seq[PushTokenReplacement]):
  def isEmpty = badTokens.isEmpty && replacements.isEmpty

  def ++(other: PushSummary): PushSummary =
    PushSummary(badTokens ++ other.badTokens, replacements ++ other.replacements)

  private def section[T](xs: Seq[T]) = xs.mkString(
    FileUtils.lineSep + FileUtils.lineSep,
    FileUtils.lineSep,
    FileUtils.lineSep + FileUtils.lineSep
  )

  private def tokensList = section(badTokens)
  private def replacementsList =
    section(replacements.map(r => s"${r.oldToken} to ${r.newToken} for ${r.device}"))

  def describe =
    s"Bad tokens: ${badTokens.size}. $tokensList Replacements: ${replacements.size}. $replacementsList"

object PushSummary:
  val empty = PushSummary(Nil, Nil)

trait PushClient[T <: Token]:
  def push(notification: BoatNotification, to: T): IO[PushSummary]

trait PushEndpoint:
  def push(notification: BoatNotification, to: PushDevice): IO[PushSummary]
