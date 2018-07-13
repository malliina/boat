package controllers

import com.malliina.http.OkClient
import com.malliina.play.auth.{AuthConf, AuthConfReader, BasicAuthHandler, CodeValidationConf, StandardCodeValidator}
import controllers.Social.{EmailKey, GoogleCookie, ProviderCookieName}
import play.api.{Configuration, Mode}
import play.api.mvc.{AbstractController, ControllerComponents, Cookie, DiscardingCookie}

import scala.concurrent.ExecutionContext

object Social {
  val EmailKey = "email"
  val ProviderCookieName = "provider"
  val GoogleCookie = "google"

  def apply(mode: Mode, conf: Configuration, http: OkClient, comps: ControllerComponents, ec: ExecutionContext) = {
    val authConf =
      if (mode == Mode.Test) AuthConf("test-id", "test-secret")
      else AuthConfReader.conf(conf).google
    new Social(authConf, comps, http)(ec)
  }
}

class Social(googleConf: AuthConf, comps: ControllerComponents, http: OkClient)(implicit ec: ExecutionContext)
  extends AbstractController(comps) {

  val lastIdKey = "last_id"
  val handler = new BasicAuthHandler(routes.BoatController.index(), lastIdKey, sessionKey = EmailKey)

  val googleValidator = StandardCodeValidator(
    CodeValidationConf.google(
      routes.Social.googleCallback(),
      handler,
      googleConf,
      http)
  )

  def google = Action.async { req => googleValidator.start(req) }

  def googleCallback = Action.async { req =>
    googleValidator.validateCallback(req).map { r =>
      if (r.header.status < 400) r.withCookies(Cookie(ProviderCookieName, GoogleCookie))
      else r
    }
  }

  def logout = Action {
    Redirect(routes.BoatController.index())
      .withNewSession
      .discardingCookies(DiscardingCookie(ProviderCookieName))
  }
}
