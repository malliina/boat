package com.malliina.boat

import com.malliina.boat.db.{BoatSchema, DatabaseConf, DatabaseUserManager}
import com.malliina.play.app.DefaultApp
import controllers.{AssetsComponents, BoatController}
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import play.api.routing.Router
import play.filters.HttpFiltersComponents
import play.filters.headers.SecurityHeadersConfig
import router.Routes

class AppLoader extends DefaultApp(new AppComponents(_))

class AppComponents(context: Context)
  extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with AssetsComponents {

  val allowedCsp = Seq(
    "*.mapbox.com"
  )
  val allowedEntry = allowedCsp.mkString(" ")

  val csp = s"default-src 'self' 'unsafe-inline' $allowedEntry; connect-src *; img-src 'self' data: blob:; worker-src blob:;"
  override lazy val securityHeadersConfig = SecurityHeadersConfig(contentSecurityPolicy = Option(csp))

  val mode = environment.mode
  val html = BoatHtml(mode)
  val databaseConf = DatabaseConf(mode, configuration)
  val schema = BoatSchema(databaseConf)
  val users = DatabaseUserManager(schema, executionContext)
  val home = new BoatController(html, users, controllerComponents, assets)(actorSystem, materializer)
  override val router: Router = new Routes(httpErrorHandler, home)
}
