package com.malliina.boat.http4s

import cats.data.Kleisli
import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import com.malliina.boat.ais.BoatMqttClient
import com.malliina.boat.auth.{GoogleTokenAuth, JWT}
import com.malliina.boat.db.{DoobieDatabase, DoobieGPSDatabase, DoobiePushDatabase, DoobieTrackInserts, DoobieTracksDatabase, DoobieUserManager}
import com.malliina.boat.html.BoatHtml
import com.malliina.boat.http4s.Service.BoatComps
import com.malliina.boat.push.BoatPushService
import com.malliina.boat.{AppMeta, BoatConf, S3Client}
import com.malliina.http.io.HttpClientIO
import com.malliina.util.AppLogger
import com.malliina.web.GoogleAuthFlow
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.HSTS
import org.http4s.server.{Router, Server}
import org.http4s.{HttpApp, HttpRoutes, Request, Response}

import scala.concurrent.ExecutionContext

case class ServerComponents(app: Service, handler: HttpApp[IO], server: Server[IO])

object Server extends IOApp {
  val log = AppLogger(getClass)
  val port = 9000

  def server(conf: BoatConf, port: Int = port): Resource[IO, ServerComponents] = for {
    blocker <- Blocker[IO]
    service <- appService(conf)
    handler = makeHandler(service, blocker)
    _ <- Resource.liftF(
      IO(log.info(s"Binding on port $port using app version ${AppMeta.default.gitHash}..."))
    )
    server <- BlazeServerBuilder[IO](ExecutionContext.global)
      .bindHttp(port = port, "0.0.0.0")
      .withHttpApp(handler)
      .resource
  } yield ServerComponents(service, handler, server)

  def appService(conf: BoatConf): Resource[IO, Service] = for {
    blocker <- Blocker[IO]
    db <- DoobieDatabase.withMigrations(conf.db, blocker)
    trackInserts = DoobieTrackInserts(db)
    gps = DoobieGPSDatabase(db)
    ais = BoatMqttClient(conf.mode)
    streams <- Resource.liftF(BoatStreams(trackInserts, ais))
    deviceStreams <- Resource.liftF(GPSStreams(gps))
  } yield {
    val http = HttpClientIO()
    val auth = Http4sAuth(JWT(conf.secret))
    val users = DoobieUserManager(db)
    val googleAuth = GoogleTokenAuth(conf.google.web.id, conf.google.ios.id, http)
    val authComps = AuthComps(googleAuth, auth, GoogleAuthFlow(conf.google.webAuthConf, http))
    val auths = new AuthService(users, authComps)
    val tracksDatabase = DoobieTracksDatabase(db)
    val push = DoobiePushDatabase(db, BoatPushService(conf.push, contextShift))
    val comps = BoatComps(
      BoatHtml(conf.mode),
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

  def makeHandler(service: Service, blocker: Blocker) = HSTS {
    orNotFound {
      Router(
        "/" -> service.routes,
        "/assets" -> StaticService(blocker, contextShift).routes
      )
    }
  }

  def orNotFound(rs: HttpRoutes[IO]): Kleisli[IO, Request[IO], Response[IO]] =
    Kleisli(req =>
      rs.run(req)
        .getOrElseF(BasicService.notFoundReq(req))
        .handleErrorWith(BasicService.errorHandler)
    )

  override def run(args: List[String]): IO[ExitCode] =
    server(BoatConf.load).use(_ => IO.never).as(ExitCode.Success)
}
