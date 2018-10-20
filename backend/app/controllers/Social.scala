package controllers

import com.malliina.http.OkClient
import com.malliina.play.auth.{AuthConf, BasicAuthHandler, GoogleCodeValidator, OAuthConf}
import controllers.Social.{EmailKey, GoogleCookie, ProviderCookieName}
import play.api.mvc.{AbstractController, ControllerComponents, Cookie, DiscardingCookie}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, DurationInt}

object Social {
  val EmailKey = "email"
  val GoogleCookie = "google"
  val ProviderCookieName = "provider"

  def apply(authConf: AuthConf, http: OkClient, comps: ControllerComponents, ec: ExecutionContext) =
    new Social(authConf, comps, http)(ec)
}

class Social(googleConf: AuthConf, comps: ControllerComponents, http: OkClient)(implicit ec: ExecutionContext)
  extends AbstractController(comps) {
  val providerCookieDuration: Duration = 3650.days
  val handler = BasicAuthHandler(routes.BoatController.index(), sessionKey = EmailKey)
  val oauthConf = OAuthConf(routes.Social.googleCallback(), handler, googleConf, http)
  val googleValidator = GoogleCodeValidator(oauthConf)

  def google = Action.async { req =>
    googleValidator.startHinted(req, req.cookies.get(handler.lastIdKey).map(_.value))
  }

  def googleCallback = Action.async { req =>
    googleValidator.validateCallback(req).map { r =>
      val cookie = Cookie(ProviderCookieName, GoogleCookie, Option(providerCookieDuration.toSeconds.toInt))
      if (r.header.status < 400) r.withCookies(cookie)
      else r
    }
  }

  def logout = Action {
    Redirect(routes.BoatController.index())
      .withNewSession
      .discardingCookies(DiscardingCookie(ProviderCookieName), DiscardingCookie(handler.lastIdKey))
  }
}
