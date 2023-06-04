package com.malliina.boat

import cats.effect.Async
import cats.effect.std.Dispatcher
import ch.qos.logback.classic.Level
import com.malliina.http.io.HttpClientF2
import com.malliina.logback.LogbackUtils
import com.malliina.logstreams.client.LogstreamsUtils

object Logging:
  private val defaultLevel = if BuildInfo.mode == "test" then Level.OFF else Level.INFO
  private val userAgent = s"Boat-Tracker/${BuildInfo.version} (${BuildInfo.gitHash.take(7)})"
  private val levels = Map(
    "org.http4s.ember.server.EmberServerBuilderCompanionPlatform" -> Level.OFF
  )
  def init() = LogbackUtils.init(rootLevel = defaultLevel, levelsByLogger = levels)

  def install[F[_]: Async](d: Dispatcher[F], http: HttpClientF2[F]) =
    LogstreamsUtils.installIfEnabled("boat", userAgent, d, http)
