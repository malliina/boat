package com.malliina.boat

import com.malliina.boat.auth.EmailAuth
import com.malliina.boat.db.Conf
import com.malliina.boat.push.PushEndpoint

import java.nio.file.Paths

//import java.nio.file.Paths
//import java.util.concurrent.{Executors, TimeUnit}
//
//import com.malliina.boat.ais.BoatMqttClient
//import com.malliina.boat.auth.{EmailAuth, GoogleTokenAuth}
//import com.malliina.boat.db._
//import com.malliina.boat.html.BoatHtml
//import com.malliina.boat.http.CSRFConf._
//import com.malliina.boat.parsing.{BoatService, DeviceService}
//import com.malliina.boat.push._
//import com.malliina.http.OkClient
//import com.typesafe.config.ConfigFactory
//import controllers._
//import play.api.ApplicationLoader.Context
//import play.api.http.{HttpConfiguration, HttpErrorHandler}
//import play.api.mvc.EssentialFilter
//import play.api.routing.Router
//import play.api.{BuiltInComponentsFromContext, Configuration, Mode}
//
//import scala.concurrent.{ExecutionContext, Future}

object LocalConf2 {
  val localConfFile =
    Paths.get(sys.props("user.home")).resolve(".boat/boat.conf")
//  val localConf = Configuration(ConfigFactory.parseFile(localConfFile.toFile))
}

// Put modules that have different implementations in dev, prod or tests here.
trait AppComps {
  def appConf: AppConf
  def pushService: PushEndpoint
  def emailAuth: EmailAuth
  def databaseConf: Conf
}

//class ProdAppBuilder(conf: Configuration, http: OkClient, ec: ExecutionContext) extends AppComps {
//  override val appConf = AppConf(conf)
//  override val pushService: PushEndpoint = BoatPushService(conf, ec)
//  override val emailAuth: EmailAuth =
//    GoogleTokenAuth(appConf.webClientId, appConf.iosClientId, http, ec)
//  override val databaseConf: Conf =
//    Conf.fromConf(conf).fold(msg => throw new Exception(msg), identity)
//}

//class AppComponents(
//  init: (Configuration, OkClient, ExecutionContext) => AppComps,
//  context: Context
//) extends BuiltInComponentsFromContext(context)
//  with HttpFiltersComponents
//  with AssetsComponents {
//  override lazy val httpErrorHandler: HttpErrorHandler = BoatErrorHandler
//  val http = OkClient.default
//  override val configuration: Configuration =
//    LocalConf.localConf.withFallback(context.initialConfiguration)
//  val builder = init(configuration, http, executionContext)
//
//  val appConf = builder.appConf
//  val mode = environment.mode
//  val isProd = mode == Mode.Prod
//  val prodHosts = Seq(
//    "www.boat-tracker.com",
//    "api.boat-tracker.com",
//    "beta.boat-tracker.com",
//    "boat-tracker.com"
//  )
//  val devHosts = Seq("localhost")
//  val allowedHosts = if (isProd) prodHosts else prodHosts ++ devHosts
////  override lazy val allowedHostsConfig = AllowedHostsConfig(allowedHosts)
//
//  override lazy val csrfConfig = CSRFConfig(
//    tokenName = CsrfTokenName,
//    cookieName = Option(CsrfCookieName),
//    headerName = CsrfHeaderName,
//    shouldProtect = rh => !rh.headers.get(CsrfHeaderName).contains(CsrfTokenNoCheck)
//  )
//
//  override def httpFilters: Seq[EssentialFilter] =
//    Seq(new GzipFilter(), csrfFilter, securityHeadersFilter)
//
//  val csps = Seq(
//    "default-src 'self' 'unsafe-inline' *.mapbox.com",
//    "font-src 'self' data: https://fonts.gstatic.com https://use.fontawesome.com",
//    "style-src 'self' 'unsafe-inline' https://maxcdn.bootstrapcdn.com https://fonts.googleapis.com *.mapbox.com https://use.fontawesome.com",
//    "connect-src * https://*.tiles.mapbox.com https://api.mapbox.com",
//    "img-src 'self' data: blob:",
//    "child-src blob:",
//    "script-src 'unsafe-eval' 'self' *.mapbox.com npmcdn.com https://cdnjs.cloudflare.com"
//  )
//  val csp = csps mkString "; "
//  override lazy val securityHeadersConfig = SecurityHeadersConfig(
//    contentSecurityPolicy = Option(csp)
//  )
//  val defaultHttpConf =
//    HttpConfiguration.fromConfiguration(configuration, environment)
//  // Sets sameSite = None, otherwise the Google auth redirect will wipe out the session state
//  override lazy val httpConfiguration =
//    defaultHttpConf.copy(
//      session = defaultHttpConf.session
//        .copy(cookieName = "boatSession", sameSite = None)
//    )
//
//  val html = BoatHtml(mode)
//  val dbConf = builder.databaseConf
//  val ds = Conf.dataSource(dbConf)
//  val executor = Executors.newFixedThreadPool(20)
//  val dbExecutor = ExecutionContext.fromExecutor(executor)
//
//  // Services
//  val doobie = DoobieDatabase.withMigrations(dbConf, dbExecutor)
//  val users: UserManager = DoobieUserManager(doobie)
//  users.initUser()
//  val tracks: TracksSource with StatsSource = DoobieTracksDatabase(doobie)
//  val inserts = DoobieTrackInserts(doobie)
//  val gps: GPSSource = DoobieGPSDatabase(doobie)
//  lazy val pushService: PushEndpoint = builder.pushService
//  lazy val push: PushService = DoobiePushDatabase(doobie, pushService)
//  val googleAuth: EmailAuth = builder.emailAuth
//  val ais = BoatMqttClient(mode)
//  val boatService = BoatService(ais, inserts, actorSystem, materializer)
//  val deviceService = DeviceService(gps, actorSystem, materializer)
//
//  // Controllers
//  val signIn = Social(appConf.web, http, controllerComponents, executionContext)
//  val files = new FileController(
//    S3Client(),
//    new FileController.BlockingActions(
//      actorSystem,
//      controllerComponents.parsers.default
//    ),
//    controllerComponents
//  )
//  lazy val pushCtrl =
//    new PushController(push, googleAuth, users, controllerComponents)
//  lazy val appCtrl =
//    new AppController(googleAuth, users, assets, controllerComponents)
//  lazy val boatCtrl = new BoatController(
//    appConf.mapboxToken,
//    html,
//    users,
//    googleAuth,
//    boatService,
//    deviceService,
//    tracks,
//    inserts,
//    TrackImporter(inserts, actorSystem, executionContext),
//    tracks,
//    push,
//    controllerComponents
//  )(actorSystem, materializer)
//  val docs = new DocsController(controllerComponents)
//  val graphs = new GraphController(controllerComponents)
//  override lazy val router: Router = new Routes(
//    httpErrorHandler,
//    boatCtrl,
//    appCtrl,
//    pushCtrl,
//    graphs,
//    signIn,
//    docs,
//    files
//  )
//
//  applicationLifecycle.addStopHook(() =>
//    Future.successful {
//      ais.close()
//      deviceService.close()
//      boatService.close()
//      http.close()
//      doobie.close()
//      executor.shutdown()
//      executor.awaitTermination(5, TimeUnit.SECONDS)
//    }
//  )
//}