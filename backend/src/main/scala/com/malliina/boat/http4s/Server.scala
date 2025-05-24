package com.malliina.boat.http4s

import cats.effect.*
import com.malliina.boat.auth.{EmailAuth, JWT, TokenEmailAuth}
import com.malliina.boat.db.*
import com.malliina.boat.push.{BoatPushService, PushEndpoint}
import com.malliina.boat.{BoatConf, Logging}
import com.malliina.http.HttpClient
import com.malliina.http.io.{HttpClientF2, HttpClientIO}
import fs2.compression.Compression
import fs2.io.file.Files
import fs2.io.net.Network
import org.http4s.server.Server

import scala.concurrent.duration.Duration

case class ServerComponents[F[_]](app: Service[F], server: Server)

trait AppCompsBuilder[F[_]]:
  def http: Resource[F, HttpClientF2[F]]
  def build(conf: BoatConf, http: HttpClient[F]): AppComps[F]

object AppCompsBuilder:
  def prod[F[_]: Async]: AppCompsBuilder[F] = new AppCompsBuilder[F]:
    override def http: Resource[F, HttpClientF2[F]] = HttpClientIO.resource
    override def build(conf: BoatConf, http: HttpClient[F]): AppComps[F] =
      ProdAppComps(conf, http)

// Put modules that have different implementations in dev, prod or tests here.
trait AppComps[F[_]]:
  def customJwt: CustomJwt
  def pushService: PushEndpoint[F]
  def emailAuth: EmailAuth[F]

class ProdAppComps[F[_]: Async](conf: BoatConf, httpClient: HttpClient[F]) extends AppComps[F]:
  override val customJwt: CustomJwt = CustomJwt(JWT(conf.secret))
  override val pushService: PushEndpoint[F] = BoatPushService.fromConf(conf.push, httpClient)
  override val emailAuth: EmailAuth[F] =
    TokenEmailAuth.default(
      conf.google.web.id,
      conf.google.ios.id,
      conf.microsoft.boat.id,
      conf.microsoft.car.id,
      httpClient,
      customJwt
    )

object Server extends IOApp with ServerResources:
  override def runtimeConfig =
    super.runtimeConfig.copy(cpuStarvationCheckInitialDelay = Duration.Inf)
  Logging.init()

  override def run(args: List[String]): IO[ExitCode] =
    for
      conf <- BoatConf.parseF[IO]
      webServer <- server[IO](conf, AppCompsBuilder.prod).use(_ => IO.never).as(ExitCode.Success)
    yield webServer
