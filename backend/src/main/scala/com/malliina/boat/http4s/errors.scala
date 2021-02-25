package com.malliina.boat.http4s

import cats.effect.IO
import com.malliina.boat.Errors
import com.malliina.web.AuthError
import org.http4s.{DecodeFailure, Headers, HttpVersion, Request, Response, Status}
import play.api.libs.json.{JsError, JsValue, Json}

class MissingCredentialsException(error: MissingCredentials) extends IdentityException(error)

class IdentityException(val error: IdentityError) extends Exception

class JsonException(val error: JsError, val json: JsValue) extends DecodeFailure {
  override def message: String = s"JSON exception for '$json'."
  override def cause: Option[Throwable] = None
  override def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F] =
    Response(Status.BadRequest, httpVersion).withEntity(Errors.fromJson(error))(
      JsonInstances.jsonEncoderOf[F, Errors]
    )
}

object IdentityException {
  def apply(error: IdentityError): IdentityException = error match {
    case mc @ MissingCredentials(_, _) => new MissingCredentialsException(mc)
    case other                         => new IdentityException(other)
  }
}

sealed trait IdentityError {
  def headers: Headers
}

case class MissingCredentials(message: String, headers: Headers) extends IdentityError
case class JWTError(error: AuthError, headers: Headers) extends IdentityError

class InvalidRequest(val req: Request[IO], val errors: Errors)
  extends Exception(s"Invalid request to '${req.uri}'. ${errors.message}.") {
  def message = getMessage
}
