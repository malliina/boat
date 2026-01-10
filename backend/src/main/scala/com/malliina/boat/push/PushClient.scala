package com.malliina.boat.push

import cats.data.NonEmptyList
import com.malliina.boat.db.PushDevice
import com.malliina.boat.{PushTokenType, PushToken}
import com.malliina.push.Token
import com.malliina.push.apns.{APNSError, APNSHttpResult, BadDeviceToken, Unregistered}
import com.malliina.push.gcm.MappedGCMResponse
import com.malliina.push.gcm.MappedGCMResponse.TokenReplacement
import com.malliina.util.FileUtils

import java.time.Instant

case class PushTokenReplacement(oldToken: PushToken, newToken: PushToken, device: PushTokenType)

object PushTokenReplacement:
  def apply(gcm: TokenReplacement): PushTokenReplacement =
    PushTokenReplacement(
      PushToken.unsafe(gcm.oldToken.token),
      PushToken.unsafe(gcm.newToken.token),
      PushTokenType.Android
    )

case class PushSummary(iosResults: Seq[APNSHttpResult], gcmResults: Seq[MappedGCMResponse]):
  private def gcmUninstalled = gcmResults.flatMap(_.uninstalled)
  def isEmpty = iosResults.isEmpty && gcmResults.isEmpty
  def iosSuccesses = iosResults.filter(_.error.isEmpty).map(_.token)
  def gcmSuccesses = gcmResults.flatMap: res =>
    res.ids
      .zip(res.response.results)
      .collect:
        case (token, res) if res.error.isEmpty => token
  def badTokens = iosResults
    .filter(res => res.error.exists(err => PushSummary.removableErrors.exists(_ == err)))
    .map(res => PushToken.unsafe(res.token.token)) ++ gcmUninstalled.map(t => PushToken.unsafe(t.token))
  def replacements = gcmResults.flatMap(_.replacements).map(PushTokenReplacement.apply)
  def noBadTokensOrReplacements = badTokens.isEmpty && replacements.isEmpty
  def ++(other: PushSummary): PushSummary = PushSummary(
    iosResults ++ other.iosResults,
    gcmResults ++ other.gcmResults
  )

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
  val removableErrors: NonEmptyList[APNSError] = NonEmptyList.of(BadDeviceToken, Unregistered)

  def merge(results: List[PushSummary]): PushSummary = results.fold(PushSummary.empty)(_ ++ _)

trait PushClient[F[_], T <: Token]:
  def push(notification: SourceNotification, geo: PushGeo, to: T): F[PushSummary]

trait PushEndpoint[F[_]]:
  def push(
    notification: SourceNotification,
    geo: PushGeo,
    to: PushDevice,
    now: Instant
  ): F[PushSummary]
