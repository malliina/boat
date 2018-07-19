package controllers

import com.malliina.http.OkClient
import com.malliina.play.auth.{AuthConf, BasicAuthHandler, CodeValidationConf, GoogleCodeValidator}
import controllers.Social.{EmailKey, GoogleCookie, ProviderCookieName}
import play.api.mvc.{AbstractController, ControllerComponents, Cookie, DiscardingCookie}

import scala.concurrent.ExecutionContext

object Social {
  val EmailKey = "email"
  val ProviderCookieName = "provider"
  val GoogleCookie = "google"

  def apply(authConf: AuthConf, http: OkClient, comps: ControllerComponents, ec: ExecutionContext) =
    new Social(authConf, comps, http)(ec)
}

class Social(googleConf: AuthConf, comps: ControllerComponents, http: OkClient)(implicit ec: ExecutionContext)
  extends AbstractController(comps) {

  val lastIdKey = "last_id"
  val handler = new BasicAuthHandler(routes.BoatController.index(), lastIdKey, sessionKey = EmailKey)

  val googleValidator = GoogleCodeValidator(
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
