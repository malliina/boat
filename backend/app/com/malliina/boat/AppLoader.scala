package com.malliina.boat

import java.nio.file.Paths

import com.malliina.boat.db._
import com.malliina.boat.html.BoatHtml
import com.malliina.play.app.DefaultApp
import com.typesafe.config.ConfigFactory
import controllers.{AssetsComponents, BoatController, FileController, Social}
import play.api.ApplicationLoader.Context
import play.api.mvc.EssentialFilter
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, Configuration, Mode}
import play.filters.HttpFiltersComponents
import play.filters.headers.SecurityHeadersConfig
import play.filters.hosts.AllowedHostsConfig
import router.Routes

import scala.concurrent.Future

object AppConf {
  val localConfFile = Paths.get(sys.props("user.home")).resolve(".boat/boat.conf")
  val localConf = Configuration(ConfigFactory.parseFile(localConfFile.toFile))
}

class AppLoader extends DefaultApp(new AppComponents(_))

class AppComponents(context: Context)
  extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with AssetsComponents {

  override val configuration = context.initialConfiguration ++ AppConf.localConf
  override lazy val allowedHostsConfig = AllowedHostsConfig(Seq("boat.malliina.com", "localhost"))
  override def httpFilters: Seq[EssentialFilter] = Seq(csrfFilter, securityHeadersFilter)
  val csps = Seq(
    "default-src 'self' 'unsafe-inline' *.mapbox.com",
    "font-src 'self' data: https://fonts.gstatic.com",
    "style-src 'self' https://fonts.googleapis.com *.mapbox.com",
    "connect-src * https://*.tiles.mapbox.com https://api.mapbox.com",
    "img-src 'self' data: blob:",
    "child-src blob:",
    "script-src 'unsafe-eval' 'self' *.mapbox.com npmcdn.com"
  )
  val csp = csps mkString "; "
  override lazy val securityHeadersConfig = SecurityHeadersConfig(contentSecurityPolicy = Option(csp))

  val mode = environment.mode
  val html = BoatHtml(mode)
  val databaseConf = DatabaseConf(mode, configuration)
  if (mode != Mode.Test)
    DBMigrations.run(databaseConf)
  val schema = BoatSchema(databaseConf)
  schema.initBoat()(executionContext)
  val users: UserManager = DatabaseUserManager(schema, executionContext)
  val tracks: TracksSource = TracksDatabase(schema, executionContext)
  val mapboxToken = AccessToken(configuration.get[String]("boat.mapbox.token"))

  val signIn = Social(mode, configuration, controllerComponents, executionContext)
  val files = new FileController(
    S3Client(),
    new FileController.BlockingActions(actorSystem, controllerComponents.parsers.default),
    controllerComponents
  )
  val home = new BoatController(
    mapboxToken, html, users, tracks,
    controllerComponents, assets)(actorSystem, materializer)
  override val router: Router = new Routes(httpErrorHandler, home, signIn, files)

  applicationLifecycle.addStopHook(() => Future.successful {
    schema.close()
    signIn.okClient.close()
  })
}
