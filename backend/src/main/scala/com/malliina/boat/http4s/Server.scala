package com.malliina.boat.http4s

import cats.data.Kleisli
import cats.effect.kernel.Resource
import cats.effect.{ExitCode, IO, IOApp}
import com.malliina.boat.ais.BoatMqttClient
import com.malliina.boat.auth.{EmailAuth, JWT, TokenEmailAuth}
import com.malliina.boat.db.*
import com.malliina.boat.html.BoatHtml
import com.malliina.boat.http4s.Implicits.circeJsonEncoder
import com.malliina.boat.http4s.Service.BoatComps
import com.malliina.boat.push.{BoatPushService, PushEndpoint}
import com.malliina.boat.{AppMeta, AppMode, BoatConf, Errors, S3Client, SingleError}
import com.malliina.http.HttpClient
import com.malliina.http.io.HttpClientIO
import com.malliina.util.AppLogger
import com.malliina.web.*
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.middleware.{GZip, HSTS}
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.server.{Router, Server, ServiceErrorHandler}
import org.http4s.{HttpApp, HttpRoutes, Request, Response}

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

case class ServerComponents(app: Service, server: Server)

trait AppCompsBuilder:
  def apply(conf: BoatConf, http: HttpClient[IO]): AppComps

object AppCompsBuilder:
  val prod: AppCompsBuilder = (conf: BoatConf, http: HttpClient[IO]) => new ProdAppComps(conf, http)

// Put modules that have different implementations in dev, prod or tests here.
trait AppComps:
  def pushService: PushEndpoint
  def emailAuth: EmailAuth

class ProdAppComps(conf: BoatConf, http: HttpClient[IO]) extends AppComps:
  override val pushService: PushEndpoint = BoatPushService(conf.push, http)
  override val emailAuth: EmailAuth =
    TokenEmailAuth(conf.google.web.id, conf.google.ios.id, conf.microsoft.id, http)

object Server extends IOApp:
  val log = AppLogger(getClass)
  val port = 9000

  def server(
    conf: BoatConf,
    builder: AppCompsBuilder,
    port: Int = port
  ): Resource[IO, ServerComponents] = for
    service <- appService(conf, builder)
    _ <- Resource.eval(
      IO(log.info(s"Binding on port $port using app version ${AppMeta.default.gitHash}..."))
    )
    server <- BlazeServerBuilder[IO]
      .bindHttp(port = port, "0.0.0.0")
      .withBanner(Nil)
      .withHttpWebSocketApp(sockets => makeHandler(service, sockets))
      .withServiceErrorHandler(errorHandler)
      .resource
  yield ServerComponents(service, server)

  def appService(conf: BoatConf, builder: AppCompsBuilder): Resource[IO, Service] = for
    db <- DoobieDatabase.init(conf.db)
    users = DoobieUserManager(db)
    _ <- Resource.eval(users.initUser())
    trackInserts = TrackInserter(db)
    gps = DoobieGPSDatabase(db)
    ais <- BoatMqttClient(AppMode.fromBuild, runtime)
    streams <- BoatStreams(trackInserts, ais)
    deviceStreams <- GPSStreams(gps)
  yield
    val http = HttpClientIO()
    val appComps = builder(conf, http)
    val auth = Http4sAuth(JWT(conf.secret))
    val googleAuth = appComps.emailAuth
    val appleToken =
      if conf.apple.enabled then SignInWithApple(conf.apple).signInWithAppleToken(Instant.now())
      else
        log.info("Sign in with Apple is disabled.")
        ClientSecret("disabled")
    val appleAuthConf = AuthConf(conf.apple.clientId, appleToken)
    val authComps = AuthComps(
      googleAuth,
      auth,
      GoogleAuthFlow(conf.google.webAuthConf, http),
      MicrosoftAuthFlow(conf.microsoft.webAuthConf, http),
      AppleAuthFlow(appleAuthConf, AppleTokenValidator(Seq(conf.apple.clientId)), http)
    )
    val auths = new AuthService(users, authComps)
    val tracksDatabase = DoobieTracksDatabase(db)
    val push = DoobiePushDatabase(db, appComps.pushService)
    val comps = BoatComps(
      BoatHtml.fromBuild,
      tracksDatabase,
      trackInserts,
      tracksDatabase,
      auths,
      conf.mapbox.token,
      S3Client(),
      push,
      streams,
      deviceStreams
    )
    Service(comps)

  def makeHandler(service: Service, sockets: WebSocketBuilder2[IO]) = GZip {
    HSTS {
      CSP.when(AppMode.fromBuild.isProd) {
        orNotFound {
          Router(
            "/" -> service.routes(sockets),
            "/assets" -> StaticService[IO]().routes
          )
        }
      }
    }
  }

  def orNotFound(rs: HttpRoutes[IO]): Kleisli[IO, Request[IO], Response[IO]] =
    Kleisli { req =>
      rs.run(req)
        .getOrElseF(BasicService.notFoundReq(req))
        .handleErrorWith(BasicService.errorHandler)
    }

  private def errorHandler: ServiceErrorHandler[IO] = req => { case NonFatal(t) =>
    log.error(s"Server error for ${req.method} '${req.uri.renderString}'.", t)
    BasicService.serverError(Errors(SingleError("Server error.", "server")))
  }

  override def run(args: List[String]): IO[ExitCode] =
    server(BoatConf.parse(), AppCompsBuilder.prod).use(_ => IO.never).as(ExitCode.Success)
