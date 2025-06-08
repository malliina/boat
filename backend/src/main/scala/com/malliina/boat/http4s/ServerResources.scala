package com.malliina.boat.http4s

import cats.data.Kleisli
import cats.effect.std.Dispatcher
import cats.effect.{Async, Resource, Sync}
import cats.syntax.all.catsSyntaxApplicativeError
import com.comcast.ip4s.{Port, host, port}
import com.malliina.boat.*
import com.malliina.boat.ais.BoatMqttClient
import com.malliina.boat.auth.JWT
import com.malliina.boat.cars.PolestarService
import com.malliina.boat.db.*
import com.malliina.boat.graph.Graph
import com.malliina.boat.html.BoatHtml
import com.malliina.boat.http4s.JsonInstances.circeJsonEncoder
import com.malliina.boat.parking.Parking
import com.malliina.boat.push.LiveActivityManager
import com.malliina.database.DoobieDatabase
import com.malliina.http.{CSRFConf, Errors, SingleError}
import com.malliina.http4s.CSRFUtils
import com.malliina.http4s.CSRFUtils.CSRFChecker
import com.malliina.polestar.Polestar
import com.malliina.util.AppLogger
import com.malliina.values.ErrorMessage
import com.malliina.web.*
import fs2.compression.Compression
import fs2.io.file.Files
import fs2.io.net.Network
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.{CSRF, GZip, HSTS}
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.{Http, HttpRoutes, Request, Response}

import java.io.IOException
import java.time.Instant
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal

trait ServerResources:
  private val log = AppLogger(getClass)

  private val port: Port =
    sys.env.get("SERVER_PORT").flatMap(s => Port.fromString(s)).getOrElse(port"9000")

  val csrfConf = CSRFConf.default

  def server[F[+_]: {Async, Network, Files, Compression}](
    conf: BoatConf,
    builder: AppCompsBuilder[F],
    port: Port = port
  ): Resource[F, ServerComponents[F]] =
    val F = Sync[F]
    val csrfUtils = CSRFUtils(csrfConf)
    for
      csrf <- Resource.eval[F, CSRF[F, F]](
        csrfUtils.default[F](onFailure = CSRFUtils.defaultFailure[F])
      )
      service <- appService(conf, builder, csrf, csrfConf)
      _ <- Resource.eval(
        F.delay(log.info(s"Binding on port $port using app version ${AppMeta.default.gitHash}..."))
      )
      server <- EmberServerBuilder
        .default[F]
        .withIdleTimeout(30.days)
        .withHost(host"0.0.0.0")
        .withPort(port)
        .withHttpWebSocketApp: sockets =>
          makeHandler(service, sockets, csrfMiddleware(csrfUtils.middleware(csrf)))
        .withErrorHandler(errorHandler)
        .withShutdownTimeout(1.millis)
        .build
    yield ServerComponents(service, server)

  private def csrfMiddleware[F[_]](fallback: CSRFChecker[F]): CSRFChecker[F] =
    http =>
      Kleisli: req =>
        val path = req.uri.path.segments.map(_.encoded).toList
        // Apple POSTs the callback; can't modify their request; it would fail CSRF check by default; so need to
        // make an exception for them
        val isAppleCallback = path == List("sign-in", "callbacks", "apple")
        if isAppleCallback then http(req)
        else fallback(http)(req)

  def appService[F[+_]: {Async, Files}](
    conf: BoatConf,
    builder: AppCompsBuilder[F],
    csrf: CSRF[F, F],
    csrfConf: CSRFConf
  ): Resource[F, Service[F]] =
    for
      dispatcher <- Dispatcher.parallel[F]
      http <- builder.http
      polestar <- Polestar.resource[F]
      _ <- Resource.eval(Logging.install(dispatcher, http))
      db <-
        log.info(s"Using database at ${conf.db.url}...")
        val dbConf = conf.db
        if conf.isFull then DoobieDatabase.init(dbConf)
        else Resource.eval(DoobieDatabase.fast(dbConf))
      users = DoobieUserManager(db)
      _ <-
        if conf.isFull then Resource.eval(users.initUser()) else Resource.unit
      trackInserts = TrackInserter(db)
      vesselDb = BoatVesselDatabase(db)
      ais <- BoatMqttClient.build(conf.ais.enabled, dispatcher)
      streams <- BoatStreams.resource(trackInserts, vesselDb, ais)
      s3 <- S3Client.build[F]()
      graph <- Resource.eval(Graph.load[F])
      reverseGeo <- Resource.eval:
        if conf.isTest then Async[F].pure(Geocoder.noop)
        else ThrottlingGeocoder.default(conf.mapbox.token, http)
      appComps = builder.build(conf, http)
      tracksDatabase = DoobieTracksDatabase(db)
      _ <- LiveActivityManager(appComps.pushService, tracksDatabase, reverseGeo, db).polling
    yield
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
        MicrosoftAuthFlow(conf.microsoft.boat.webAuthConf, http),
        MicrosoftAuthFlow(conf.microsoft.car.webAuthConf, http),
        AppleAuthFlow(appleWebConf, appleValidator, http),
        AppleAuthFlow(appleAppConf, appleValidator, http),
        appComps.customJwt
      )
      val polestarService = PolestarService(users, polestar)
      val auths = AuthService(users, authComps)
      val tracksDatabase = DoobieTracksDatabase(db)
      val push = DoobiePushDatabase(db, appComps.pushService)
      val comps = BoatComps(
        BoatHtml.fromBuild(SourceType.Boat, csrfConf),
        BoatHtml.fromBuild(SourceType.Vehicle, csrfConf),
        tracksDatabase,
        vesselDb,
        trackInserts,
        tracksDatabase,
        auths,
        conf.mapbox.token,
        s3,
        push,
        streams,
        reverseGeo,
        Parking(http, reverseGeo),
        polestarService
      )
      Service(comps, graph, csrf, csrfConf)

  private def makeHandler[F[_]: {Async, Compression, Files}](
    service: Service[F],
    sockets: WebSocketBuilder2[F],
    csrfChecker: CSRFUtils.CSRFChecker[F]
  ): Http[F, F] =
    csrfChecker:
      GZip:
        HSTS:
          CSP.when(AppMode.fromBuild.isProd):
            orNotFound:
              Router(
                "/" -> service.routes(sockets),
                "/assets" -> StaticService[F].routes
              )

  private def orNotFound[F[_]: Sync](rs: HttpRoutes[F]): Kleisli[F, Request[F], Response[F]] =
    Kleisli: req =>
      rs.run(req)
        .getOrElseF(BoatBasicService[F].notFoundReq(req))
        .handleErrorWith(t => BoatBasicService[F].errorHandler(t, req))

  private def errorHandler[F[_]: Sync]: PartialFunction[Throwable, F[Response[F]]] =
    case ioe: IOException
        if ioe.message
          .exists(msg => BoatBasicService.noisyErrorMessages.exists(n => msg.startsWith(n))) =>
      serverErrorResponse("Generic server IO error.")
    case NonFatal(t) =>
      log.error(s"Server error: '${t.getMessage}'.", t)
      serverErrorResponse("Generic server error.")

  def serverErrorResponse[F[_]: Sync](msg: String) =
    BoatBasicService[F].serverError(Errors(SingleError(ErrorMessage(msg), "server")))
