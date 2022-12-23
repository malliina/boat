package com.malliina.logback

import cats.effect.IO
import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import cats.syntax.all.toFlatMapOps
import com.malliina.util.AppLogger
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Logger
import com.malliina.boat.BuildInfo
import com.malliina.http.HttpClient
import com.malliina.http.io.HttpClientF2
import com.malliina.logback.fs2.FS2AppenderComps
import com.malliina.logstreams.client.FS2Appender

object LogstreamsUtils:
  def install[F[_]: Async](d: Dispatcher[F], http: HttpClientF2[F]): F[Unit] =
    val enabled = sys.env.get("LOGSTREAMS_ENABLED").contains("true")
    if enabled then
      FS2Appender
        .default(
          d,
          http,
          Map("User-Agent" -> s"Boat-Tracker/${BuildInfo.version} (${BuildInfo.gitHash.take(7)})")
        )
        .flatMap { appender =>
          Async[F].delay {
            appender.setName("LOGSTREAMS")
            appender.setEndpoint("wss://logs.malliina.com/ws/sources")
            appender.setUsername(sys.env.getOrElse("LOGSTREAMS_USER", "boat"))
            appender.setPassword(sys.env.getOrElse("LOGSTREAMS_PASS", ""))
            appender.setEnabled(enabled)
            LogbackUtils.installAppender(appender)
          }
        }
    else Async[F].unit
