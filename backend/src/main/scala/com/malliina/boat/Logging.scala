package com.malliina.boat

import cats.effect.IO
import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import cats.syntax.all.toFlatMapOps
import com.malliina.util.AppLogger
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import com.malliina.boat.BuildInfo
import com.malliina.http.HttpClient
import com.malliina.http.io.HttpClientF2
import com.malliina.logback.LogbackUtils
import com.malliina.logback.fs2.FS2AppenderComps
import com.malliina.logstreams.client.{FS2Appender, LogstreamsUtils}

object Logging:
  private val defaultLevel = if BuildInfo.mode == "test" then Level.OFF else Level.INFO
  private val userAgent = s"Boat-Tracker/${BuildInfo.version} (${BuildInfo.gitHash.take(7)})"
  private val levels = Map(
    "org.http4s.ember.server.EmberServerBuilderCompanionPlatform" -> Level.OFF
  )
  def init() = LogbackUtils.init(rootLevel = defaultLevel, levelsByLogger = levels)

  def install[F[_]: Async](d: Dispatcher[F], http: HttpClientF2[F]) =
    LogstreamsUtils.install("boat", userAgent, d, http)
