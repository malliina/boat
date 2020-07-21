package controllers

import com.malliina.http.OkClient
import com.malliina.play.auth.{AuthConf, AuthError, AuthHandler, BasicAuthHandler, GoogleCodeValidator, OAuthConf}
import com.malliina.play.http.HttpConstants
import com.malliina.values.Email
import controllers.Social.{EmailKey, GoogleCookie, ProviderCookieName}
import play.api.Logger
import play.api.mvc.{AbstractController, ControllerComponents, Cookie, DiscardingCookie, RequestHeader, Result}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, DurationInt}

object Social {
  val EmailKey = "boatEmail"
  val GoogleCookie = "google"
  val ProviderCookieName = "boatProvider"
  val returnUriKey = "returnUri"

  def apply(authConf: AuthConf, http: OkClient, comps: ControllerComponents, ec: ExecutionContext) =
    new Social(authConf, comps, http)(ec)
}

class Social(googleConf: AuthConf, comps: ControllerComponents, http: OkClient)(implicit
  ec: ExecutionContext
) extends AbstractController(comps) {
  val log = Logger(getClass)
  val providerCookieDuration: Duration = 3650.days

  object handler extends AuthHandler {
    val lastIdKey = "boatLastId"

    override def onAuthenticated(user: Email, req: RequestHeader): Result = {
      val returnUri = req.cookies
        .get(Social.returnUriKey)
        .map(_.value)
        .getOrElse(routes.BoatController.index().path())
      log.info(s"Logging in '$user' through OAuth code flow, returning to '$returnUri'...")
      Redirect(returnUri)
        .withSession(EmailKey -> user.email)
        .discardingCookies(DiscardingCookie(Social.returnUriKey))
        .withCookies(Cookie(lastIdKey, user.email, Option(3650.days).map(_.toSeconds.toInt)))
        .withHeaders(CACHE_CONTROL -> HttpConstants.NoCacheRevalidate)
    }

    override def onUnauthorized(error: AuthError, req: RequestHeader): Result = Unauthorized
  }
  val handler2 =
    BasicAuthHandler(routes.BoatController.index(), sessionKey = EmailKey, lastIdKey = "boatLastId")
  val oauthConf = OAuthConf(routes.Social.googleCallback(), handler, googleConf, http)
  val googleValidator = GoogleCodeValidator(oauthConf)

  def google = Action.async { req =>
    googleValidator.startHinted(req, req.cookies.get(handler.lastIdKey).map(_.value))
  }

  def googleCallback = Action.async { req =>
    googleValidator.validateCallback(req).map { r =>
      val cookie =
        Cookie(ProviderCookieName, GoogleCookie, Option(providerCookieDuration.toSeconds.toInt))
      if (r.header.status < 400) r.withCookies(cookie)
      else r
    }
  }

  def logout = Action {
    Redirect(routes.BoatController.index()).withNewSession
      .discardingCookies(DiscardingCookie(ProviderCookieName), DiscardingCookie(handler.lastIdKey))
  }
}
