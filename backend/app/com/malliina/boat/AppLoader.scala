package com.malliina.boat

import java.nio.file.Paths

import com.malliina.boat.db._
import com.malliina.play.app.DefaultApp
import com.typesafe.config.ConfigFactory
import controllers.{AssetsComponents, BoatController}
import play.api.ApplicationLoader.Context
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, Configuration}
import play.filters.HttpFiltersComponents
import play.filters.headers.SecurityHeadersConfig
import play.filters.hosts.AllowedHostsConfig
import router.Routes

class AppLoader extends DefaultApp(new AppComponents(_))

class AppComponents(context: Context)
  extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with AssetsComponents {

  val localConfFile = Paths.get(sys.props("user.home")).resolve(".boat/boat.conf")
  override val configuration = context.initialConfiguration ++ Configuration(ConfigFactory.parseFile(localConfFile.toFile))
  override lazy val allowedHostsConfig = AllowedHostsConfig(Seq("boat.malliina.com", "localhost"))
  val csp = s"default-src 'self' 'unsafe-inline' *.mapbox.com; font-src 'self' data: https://fonts.gstatic.com; style-src 'self' https://fonts.googleapis.com *.mapbox.com; connect-src * https://*.tiles.mapbox.com https://api.mapbox.com; img-src 'self' data: blob:; child-src blob:; script-src 'unsafe-eval' 'self' *.mapbox.com;"
  override lazy val securityHeadersConfig = SecurityHeadersConfig(contentSecurityPolicy = Option(csp))

  val mode = environment.mode
  val html = BoatHtml(mode)
  val databaseConf = DatabaseConf(mode, configuration)
  val schema = BoatSchema(databaseConf)
  schema.initBoat()(executionContext)
  val users: UserManager = DatabaseUserManager(schema, executionContext)
  val tracks: TracksSource = TracksDatabase(schema, executionContext)
  val mapboxToken = AccessToken(configuration.get[String]("boat.mapbox.token"))
  val home = new BoatController(
    mapboxToken, html, users, tracks,
    controllerComponents, assets)(actorSystem, materializer)
  override val router: Router = new Routes(httpErrorHandler, home)
}
