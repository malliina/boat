package controllers

import com.malliina.boat.auth.EmailAuth
import com.malliina.boat.db._
import com.malliina.boat.http.BoatRequest
import com.malliina.boat.{Errors, SingleError, UserInfo}
import com.malliina.values.Email
import controllers.AuthController.log
import controllers.Social.{EmailKey, GoogleCookie, ProviderCookieName}
import play.api.Logger
import play.api.http.Writeable
import play.api.libs.json.{Json, Writes}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

object AuthController {
  private val log = Logger(getClass)
}

abstract class AuthController(googleAuth: EmailAuth,
                              users: UserManager,
                              comps: ControllerComponents) extends AbstractController(comps) {
  implicit val ec: ExecutionContext = comps.executionContext

  implicit def writeable[T: Writes] = Writeable.writeableOf_JsValue.map[T](t => Json.toJson(t))

  protected def authAction[U](authenticate: RequestHeader => Future[U])(code: BoatRequest[U, AnyContent] => Future[Result]) =
    parsedAuth(parse.default)(authenticate)(code)

  protected def parsedAuth[U, B](p: BodyParser[B])(authenticate: RequestHeader => Future[U])(code: BoatRequest[U, B] => Future[Result]) =
    Action(p).async { req =>
      recovered(authenticate(req), req).flatMap { e =>
        e.fold(fut, t => code(BoatRequest(t, req)))
      }
    }

  protected def googleProfile(rh: RequestHeader): Future[UserInfo] =
    googleAuth.authEmail(rh).flatMap { email =>
      users.userInfo(email)
    }

  /** Fails if unauthenticated.
    */
  protected def profile(rh: RequestHeader): Future[UserInfo] =
    authAppOrWeb(rh).flatMap { email =>
      users.userInfo(email)
    }

  protected def authAppOrWeb(rh: RequestHeader): Future[Email] =
    googleAuth.authEmail(rh).recoverWith {
      case mce: MissingCredentialsException =>
        sessionEmail(rh).map(email => Future.successful(email)).getOrElse(Future.failed(mce))
    }

  protected def sessionEmail(rh: RequestHeader): Option[Email] =
    rh.session.get(EmailKey).map(Email.apply)

  protected def recovered[T](f: Future[T], rh: RequestHeader): Future[Either[Result, T]] =
    f.map[Either[Result, T]](t => Right(t)).recover {
      case mce: MissingCredentialsException =>
        log.info(s"Missing credentials in '$rh'.")
        Left(checkLoginCookie(mce.rh).getOrElse(unauth))
      case ie: IdentityException =>
        log.warn(s"Authentication failed from '$rh': '${ie.error}'.")
        val error: SingleError = ie.error match {
          case JWTError(_, err) => SingleError(err.message, err.key)
          case _ => unauthError
        }
        Left(Unauthorized(Errors(error)))
    }

  private def checkLoginCookie(rh: RequestHeader): Option[Result] =
    googleCookie(rh).map { _ =>
      log.info(s"Redir to login")
      Redirect(routes.Social.google())
    }

  protected def googleCookie(rh: RequestHeader): Option[Cookie] =
    rh.cookies.get(ProviderCookieName).filter(_.value == GoogleCookie)

  private def unauth = Unauthorized(Errors(unauthError))

  def unauthError = SingleError("Unauthorized.", "unauthorized")

  def fut[T](t: T): Future[T] = Future.successful(t)
}
