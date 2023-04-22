package com.malliina.boat.http4s

import cats.Monad
import cats.data.{Kleisli, NonEmptyList}
import cats.effect.kernel.{Async, Resource}
import cats.effect.{ExitCode, IO, IOApp, Sync}
import cats.effect.std.Dispatcher
import cats.syntax.all.catsSyntaxApplicativeError
import com.comcast.ip4s.{Port, host, port}
import com.malliina.boat.ais.BoatMqttClient
import com.malliina.boat.auth.{EmailAuth, JWT, TokenEmailAuth}
import com.malliina.boat.db.*
import com.malliina.boat.html.BoatHtml
import com.malliina.boat.http4s.Implicits.circeJsonEncoder
import com.malliina.boat.http4s.BoatComps
import com.malliina.boat.push.{BoatPushService, PushEndpoint}
import com.malliina.boat.{AppMeta, AppMode, BoatConf, BuildInfo, CarDrive, Errors, Logging, S3Client, SingleError}
import com.malliina.http.HttpClient
import com.malliina.http.io.HttpClientIO
import com.malliina.logback.LogbackUtils
import com.malliina.logstreams.client.LogstreamsUtils
import com.malliina.util.AppLogger
import com.malliina.web.*
import com.malliina.boat.message
import org.http4s.Status.Unauthorized
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.{Location, `Content-Type`, `WWW-Authenticate`}
import org.http4s.server.middleware.{GZip, HSTS}
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.server.{Router, Server, ServiceErrorHandler}
import org.http4s.{Challenge, Http, HttpApp, HttpRoutes, Request, Response}
import fs2.concurrent.Topic

import java.io.IOException
import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal

case class ServerComponents[F[_]](app: Service[F], server: Server)

trait AppCompsBuilder:
  def build[F[+_]: Sync](conf: BoatConf, http: HttpClient[F]): AppComps[F]

object AppCompsBuilder:
  def prod: AppCompsBuilder = new AppCompsBuilder:
    override def build[F[+_]: Sync](conf: BoatConf, http: HttpClient[F]): AppComps[F] =
      ProdAppComps(conf, http)

// Put modules that have different implementations in dev, prod or tests here.
trait AppComps[F[_]]:
  def customJwt: CustomJwt
  def pushService: PushEndpoint[F]
  def emailAuth: EmailAuth[F]

class ProdAppComps[F[+_]: Sync](conf: BoatConf, http: HttpClient[F]) extends AppComps[F]:
  override val customJwt: CustomJwt = CustomJwt(JWT(conf.secret))
  override val pushService: PushEndpoint[F] = BoatPushService.fromConf(conf.push, http)
  override val emailAuth: EmailAuth[F] =
    TokenEmailAuth.default(
      conf.google.web.id,
      conf.google.ios.id,
      conf.microsoft.id,
      http,
      customJwt
    )

object Server extends IOApp:
  Logging.init()
  private val log = AppLogger(getClass)

  private val port: Port =
    sys.env.get("SERVER_PORT").flatMap(s => Port.fromString(s)).getOrElse(port"9000")

  def server[F[+_]: Async](
    conf: BoatConf,
    builder: AppCompsBuilder,
    port: Port = port
  ): Resource[F, ServerComponents[F]] =
    val F = Sync[F]
    for
      service <- appService(conf, builder)
      _ <- Resource.eval(
        F.delay(log.info(s"Binding on port $port using app version ${AppMeta.default.gitHash}..."))
      )
      server <- EmberServerBuilder
        .default[F]
        .withIdleTimeout(30.days)
        .withHost(host"0.0.0.0")
        .withPort(port)
        .withHttpWebSocketApp(sockets => makeHandler(service, sockets))
        .withErrorHandler(errorHandler)
        .withShutdownTimeout(1.millis)
        .build
    yield ServerComponents(service, server)

  def appService[F[+_]: Async](conf: BoatConf, builder: AppCompsBuilder): Resource[F, Service[F]] =
    for
      dispatcher <- Dispatcher.parallel[F]
      http <- HttpClientIO.resource[F]
      _ <- Resource.eval(Logging.install(dispatcher, http))
      db <- DoobieDatabase.init(conf.db)
      users = DoobieUserManager(db)
      _ <- Resource.eval(users.initUser())
      trackInserts = TrackInserter(db)
      gps = DoobieGPSDatabase(db)
      vesselDb = BoatVesselDatabase(db)
      ais <- BoatMqttClient.build(conf.ais.enabled, AppMode.fromBuild, dispatcher)
      streams <- BoatStreams.resource(trackInserts, vesselDb, ais)
      deviceStreams <- GPSStreams.resource(gps)
      carInsertions <- Resource.eval(Topic[F, CarDrive])
      s3 <- S3Client.build[F]
    yield
      val appComps = builder.build(conf, http)
      val jwt = JWT(conf.secret)
      val auth = Http4sAuth[F](jwt)
      val googleAuth = appComps.emailAuth
      val appleConf = conf.apple
      val startupTime = Instant.now()
      val appleWebToken = SignInWithApple.secretOrDummy(appleConf, startupTime)
      val appleAppToken = SignInWithApple.secretOrDummy(
        appleConf.copy(clientId = AppleTokenValidator.boatClientId),
        startupTime
      )
      val appleWebConf = AuthConf(conf.apple.clientId, appleWebToken)
      val appleAppConf = AuthConf(AppleTokenValidator.boatClientId, appleAppToken)
      val appleValidator =
        AppleTokenValidator(Seq(conf.apple.clientId, AppleTokenValidator.boatClientId), http)
      val authComps = AuthComps(
        googleAuth,
        auth,
        GoogleAuthFlow(conf.google.webAuthConf, http),
        MicrosoftAuthFlow(conf.microsoft.webAuthConf, http),
        AppleAuthFlow(appleWebConf, appleValidator, http),
        AppleAuthFlow(appleAppConf, appleValidator, http),
        appComps.customJwt
      )
      val auths = AuthService(users, authComps)
      val tracksDatabase = DoobieTracksDatabase(db)
      val push = DoobiePushDatabase(db, appComps.pushService)
      val comps = BoatComps(
        BoatHtml.fromBuild,
        tracksDatabase,
        vesselDb,
        trackInserts,
        tracksDatabase,
        auths,
        conf.mapbox.token,
        s3,
        push,
        streams,
        deviceStreams,
        CarDatabase(db, carInsertions)
      )
      Service(comps)

  private def makeHandler[F[_]: Async](
    service: Service[F],
    sockets: WebSocketBuilder2[F]
  ): Http[F, F] =
    GZip {
      HSTS {
        CSP.when(AppMode.fromBuild.isProd) {
          orNotFound {
            Router(
              "/" -> service.routes(sockets),
              "/assets" -> StaticService[F].routes
            )
          }
        }
      }
    }

  private def orNotFound[F[_]: Sync](rs: HttpRoutes[F]): Kleisli[F, Request[F], Response[F]] =
    Kleisli { req =>
      rs.run(req)
        .getOrElseF(BasicService[F].notFoundReq(req))
        .handleErrorWith(BasicService[F].errorHandler)
    }

  private def errorHandler[F[_]: Sync]: PartialFunction[Throwable, F[Response[F]]] = {
    case ioe: IOException if ioe.message.exists(_.startsWith(BasicService.noisyErrorMessage)) =>
      serverErrorResponse("Generic server IO error.")
    case NonFatal(t) =>
      log.error(s"Server error: '${t.getMessage}'.", t)
      serverErrorResponse("Generic server error.")
  }

  def serverErrorResponse[F[_]: Sync](msg: String) =
    BasicService[F].serverError(Errors(SingleError(msg, "server")))

  override def run(args: List[String]): IO[ExitCode] =
    server[IO](BoatConf.parse(), AppCompsBuilder.prod).use(_ => IO.never).as(ExitCode.Success)
