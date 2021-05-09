package com.malliina.boat.http4s

import cats.data.Kleisli
import cats.effect.{Blocker, ContextShift, ExitCode, IO, IOApp, Resource}
import com.malliina.boat.ais.BoatMqttClient
import com.malliina.boat.auth.{EmailAuth, GoogleTokenAuth, JWT}
import com.malliina.boat.db.{DoobieDatabase, DoobieGPSDatabase, DoobiePushDatabase, DoobieTrackInserts, DoobieTracksDatabase, DoobieUserManager}
import com.malliina.boat.html.BoatHtml
import com.malliina.boat.http4s.Service.BoatComps
import com.malliina.boat.push.{BoatPushService, PushEndpoint}
import com.malliina.boat.{AppMeta, AppMode, BoatConf, S3Client}
import com.malliina.http.HttpClient
import com.malliina.http.io.HttpClientIO
import com.malliina.util.AppLogger
import com.malliina.web.{EmailAuthFlow, GoogleAuthFlow, MicrosoftAuthFlow}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{GZip, HSTS}
import org.http4s.server.{Router, Server}
import org.http4s.{HttpApp, HttpRoutes, Request, Response}

import scala.concurrent.ExecutionContext

case class ServerComponents(app: Service, handler: HttpApp[IO], server: Server[IO])

trait AppCompsBuilder {
  def apply(conf: BoatConf, http: HttpClient[IO], cs: ContextShift[IO]): AppComps
}

object AppCompsBuilder {
  val prod = new AppCompsBuilder {
    override def apply(conf: BoatConf, http: HttpClient[IO], cs: ContextShift[IO]): AppComps =
      new ProdAppComps(conf, http, cs)
  }
}

// Put modules that have different implementations in dev, prod or tests here.
trait AppComps {
  def pushService: PushEndpoint
  def emailAuth: EmailAuth
}

class ProdAppComps(conf: BoatConf, http: HttpClient[IO], cs: ContextShift[IO]) extends AppComps {
  override val pushService: PushEndpoint = BoatPushService(conf.push, http)
  override val emailAuth: EmailAuth = GoogleTokenAuth(conf.google.web.id, conf.google.ios.id, http)
}

object Server extends IOApp {
  val log = AppLogger(getClass)
  val port = 9000

  def server(
    conf: BoatConf,
    builder: AppCompsBuilder,
    port: Int = port
  ): Resource[IO, ServerComponents] = for {
    blocker <- Blocker[IO]
    service <- appService(conf, builder)
    handler = makeHandler(service, blocker)
    _ <- Resource.eval(
      IO(log.info(s"Binding on port $port using app version ${AppMeta.default.gitHash}..."))
    )
    server <- BlazeServerBuilder[IO](ExecutionContext.global)
      .bindHttp(port = port, "0.0.0.0")
      .withHttpApp(handler)
      .resource
  } yield ServerComponents(service, handler, server)

  def appService(conf: BoatConf, builder: AppCompsBuilder): Resource[IO, Service] = for {
    blocker <- Blocker[IO]
    db <- DoobieDatabase.withMigrations(conf.db, blocker)
    users = DoobieUserManager(db)
    _ <- Resource.eval(users.initUser())
    trackInserts = DoobieTrackInserts(db)
    gps = DoobieGPSDatabase(db)
    ais = BoatMqttClient(AppMode.fromBuild)
    streams <- Resource.eval(BoatStreams(trackInserts, ais))
    deviceStreams <- Resource.eval(GPSStreams(gps))
  } yield {
    val http = HttpClientIO()
    val appComps = builder(conf, http, contextShift)
    val auth = Http4sAuth(JWT(conf.secret))
    val googleAuth = appComps.emailAuth
    val authComps = AuthComps(
      googleAuth,
      auth,
      GoogleAuthFlow(conf.google.webAuthConf, http),
      MicrosoftAuthFlow(conf.microsoft.webAuthConf, http)
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
      deviceStreams,
      blocker,
      contextShift
    )
    Service(comps)
  }

  def makeHandler(service: Service, blocker: Blocker) = GZip {
    HSTS {
      orNotFound {
        Router(
          "/" -> service.routes,
          "/assets" -> StaticService(blocker, contextShift).routes
        )
      }
    }
  }

  def orNotFound(rs: HttpRoutes[IO]): Kleisli[IO, Request[IO], Response[IO]] =
    Kleisli(req =>
      rs.run(req)
        .getOrElseF(BasicService.notFoundReq(req))
        .handleErrorWith(BasicService.errorHandler)
    )

  override def run(args: List[String]): IO[ExitCode] =
    server(BoatConf.load, AppCompsBuilder.prod).use(_ => IO.never).as(ExitCode.Success)
}
