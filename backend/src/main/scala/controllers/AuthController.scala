package controllers

import com.malliina.boat.auth.EmailAuth
import com.malliina.boat.db._
import com.malliina.boat.http.UserRequest
import com.malliina.boat.{Errors, SingleError, UserInfo}
import com.malliina.util.AppLogger
import com.malliina.values.Email
import controllers.AuthController.log
import play.api.libs.json.{Json, Reads, Writes}

import scala.concurrent.{ExecutionContext, Future}

object AuthController {
  private val log = AppLogger(getClass)
}

//abstract class AuthController(
//  googleAuth: EmailAuth,
//  users: UserManager,
//  comps: ControllerComponents
//) extends AbstractController(comps) {
//  implicit val ec: ExecutionContext = comps.executionContext
//
//  implicit def writeable[T: Writes]: Writeable[T] =
//    Writeable.writeableOf_JsValue.map[T](t => Json.toJson(t))
//
//  def jsonAuth[R: Reads](code: UserRequest[UserInfo] => Future[Result]): Action[R] =
//    parsedAuth(parse.json[R])(profile) { req =>
//      code(req)
//    }
//
//  protected def authAction[U](
//    authenticate: RequestHeader => Future[U]
//  )(code: UserRequest[U] => Future[Result]): Action[AnyContent] =
//    parsedAuth(parse.default)(authenticate)(code)
//
//  protected def parsedAuth[U, B](
//    p: BodyParser[B]
//  )(
//    authenticate: RequestHeader => Future[U]
//  )(code: UserRequest[U, B] => Future[Result]): Action[B] =
//    Action(p).async { req =>
//      recovered(authenticate(req), req).flatMap { e =>
//        e.fold(fut, t => code(UserRequest(t, req)))
//      }
//    }
//
//  /** Fails if unauthenticated.
//    */
//  protected def profile(rh: RequestHeader): Future[UserInfo] =
//    authEmailOnly(rh).flatMap { email =>
//      users.userInfo(email)
//    }
//
//  private def authEmailOnly(rh: RequestHeader): Future[Email] =
//    googleAuth.authEmail(rh).recoverWith {
//      case mce: MissingCredentialsException =>
//        sessionEmail(rh).map(email => Future.successful(email)).getOrElse(Future.failed(mce))
//    }
//
//  protected def sessionEmail(rh: RequestHeader): Option[Email] =
//    rh.session.get(EmailKey).map(Email.apply)
//
//  protected def recovered[T](f: Future[T], rh: RequestHeader): Future[Either[Result, T]] =
//    f.map[Either[Result, T]](t => Right(t)).recover {
//      case mce: MissingCredentialsException =>
//        log.info(s"Missing credentials in '$rh'.")
//        Left(redirectToLoginIfGoogle(mce.rh).getOrElse(unauth))
//      case ie: IdentityException =>
//        log.warn(s"Authentication failed from '$rh': '${ie.error}'.")
//        Left(Unauthorized(toError(ie)))
//    }
//
//  protected def toError(ie: IdentityException) = {
//    val error = ie.error match {
//      case JWTError(_, err) => SingleError(err.message, err.key)
//      case _                => unauthError
//    }
//    Errors(error)
//  }
//
//  private def redirectToLoginIfGoogle(rh: RequestHeader): Option[Result] =
//    googleCookie(rh).map { _ =>
//      log.info(s"Redirecting to Google login from '${rh.uri}'...")
//      Redirect(routes.Social.google()).withCookies(Cookie(Social.returnUriKey, rh.uri))
//    }
//
//  protected def googleCookie(rh: RequestHeader): Option[Cookie] =
//    rh.cookies.get(ProviderCookieName).filter(_.value == GoogleCookie)
//
//  private def unauth = Unauthorized(Errors(unauthError))
//
//  def unauthError = SingleError("Unauthorized.", "unauthorized")
//
//  def fut[T](t: T): Future[T] = Future.successful(t)
//}
