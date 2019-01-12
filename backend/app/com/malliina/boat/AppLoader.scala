package com.malliina.boat

import java.nio.file.Paths

import com.malliina.boat.ais.MqClient
import com.malliina.boat.auth.{EmailAuth, GoogleTokenAuth}
import com.malliina.boat.db._
import com.malliina.boat.html.BoatHtml
import com.malliina.boat.http.CSRFConf._
import com.malliina.boat.push._
import com.malliina.http.OkClient
import com.malliina.play.app.DefaultApp
import com.typesafe.config.ConfigFactory
import controllers._
import play.api.ApplicationLoader.Context
import play.api.http.{HttpConfiguration, HttpErrorHandler}
import play.api.mvc.EssentialFilter
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, Configuration, Mode}
import play.filters.HttpFiltersComponents
import play.filters.csrf.CSRFConfig
import play.filters.gzip.GzipFilter
import play.filters.headers.SecurityHeadersConfig
import play.filters.hosts.AllowedHostsConfig
import router.Routes

import scala.concurrent.{ExecutionContext, Future}

object LocalConf {
  val localConfFile = Paths.get(sys.props("user.home")).resolve(".boat/boat.conf")
  val localConf = Configuration(ConfigFactory.parseFile(localConfFile.toFile))
}

class AppLoader extends DefaultApp(new AppComponents((conf, http, ec) => new ProdAppBuilder(conf, http, ec), _))

// Put modules that have different implementations in dev, prod or tests here.
trait AppBuilder {
  def appConf: AppConf

  def pushService: PushEndpoint

  def emailAuth: EmailAuth
}

class ProdAppBuilder(conf: Configuration, http: OkClient, ec: ExecutionContext) extends AppBuilder {
  override val appConf = AppConf(conf)
  override val pushService: PushEndpoint = BoatPushService(conf, ec)
  override val emailAuth = GoogleTokenAuth(appConf.webClientId, appConf.iosClientId, http, ec)
}

class AppComponents(init: (Configuration, OkClient, ExecutionContext) => AppBuilder, context: Context)
  extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with AssetsComponents {

  override lazy val httpErrorHandler: HttpErrorHandler = BoatErrorHandler
  val http = OkClient.default
  override val configuration: Configuration = context.initialConfiguration ++ LocalConf.localConf
  val builder = init(configuration, http, executionContext)

  val appConf = builder.appConf
  val allowedHosts = Seq(
    "www.boat-tracker.com",
    "boat-tracker.com",
    "boat.malliina.com",
    "localhost"
  )
  override lazy val allowedHostsConfig = AllowedHostsConfig(allowedHosts)

  override lazy val csrfConfig = CSRFConfig(
    tokenName = CsrfTokenName,
    cookieName = Option(CsrfCookieName),
    headerName = CsrfHeaderName,
    shouldProtect = rh => !rh.headers.get(CsrfHeaderName).contains(CsrfTokenNoCheck)
  )

  override def httpFilters: Seq[EssentialFilter] = Seq(new GzipFilter(), csrfFilter, securityHeadersFilter)

  val csps = Seq(
    "default-src 'self' 'unsafe-inline' *.mapbox.com",
    "font-src 'self' data: https://fonts.gstatic.com https://use.fontawesome.com",
    "style-src 'self' 'unsafe-inline' https://maxcdn.bootstrapcdn.com https://fonts.googleapis.com *.mapbox.com https://use.fontawesome.com",
    "connect-src * https://*.tiles.mapbox.com https://api.mapbox.com",
    "img-src 'self' data: blob:",
    "child-src blob:",
    "script-src 'unsafe-eval' 'self' *.mapbox.com npmcdn.com https://cdnjs.cloudflare.com"
  )
  val csp = csps mkString "; "
  override lazy val securityHeadersConfig = SecurityHeadersConfig(contentSecurityPolicy = Option(csp))
  val defaultHttpConf = HttpConfiguration.fromConfiguration(configuration, environment)
  // Sets sameSite = None, otherwise the Google auth redirect will wipe out the session state
  override lazy val httpConfiguration =
    defaultHttpConf.copy(session = defaultHttpConf.session.copy(cookieName = "boatSession", sameSite = None))

  val mode = environment.mode
  val html = BoatHtml(mode)
  val databaseConf = DatabaseConf(mode, configuration)
  if (mode != Mode.Test)
    DBMigrations.run(databaseConf)
  val schema = BoatSchema(databaseConf)
  schema.initBoat()(executionContext)
  val users: UserManager = DatabaseUserManager(schema, executionContext)
  val tracks: TracksSource = TracksDatabase(schema, executionContext)
  lazy val pushService: PushEndpoint = builder.pushService
  lazy val push = PushDatabase(schema, pushService, executionContext)

  val googleAuth: EmailAuth = builder.emailAuth
  val signIn = Social(appConf.web, http, controllerComponents, executionContext)
  val files = new FileController(
    S3Client(),
    new FileController.BlockingActions(actorSystem, controllerComponents.parsers.default),
    controllerComponents
  )
  lazy val pushCtrl = new PushController(push, googleAuth, users, controllerComponents)
  lazy val appCtrl = new AppController(googleAuth, users, assets, controllerComponents)
  lazy val boatCtrl = new BoatController(
    appConf.mapboxToken, html, users, googleAuth, tracks, MqClient(mode), push,
    controllerComponents)(actorSystem, materializer)
  val docs = new DocsController(html, controllerComponents)
  override lazy val router: Router = new Routes(httpErrorHandler, boatCtrl, appCtrl, pushCtrl, signIn, docs, files)

  applicationLifecycle.addStopHook(() => Future.successful {
    schema.close()
    http.close()
  })
}
