package com.malliina.boat.http4s

import cats.effect.IO
import com.malliina.boat.Errors
import org.http4s._
import _root_.play.api.libs.json.{JsError, JsValue}

class JsonException(val error: JsError, val json: JsValue) extends DecodeFailure {
  override def message: String = s"JSON exception for '$json'."
  override def cause: Option[Throwable] = None
  override def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F] =
    Response(Status.BadRequest, httpVersion).withEntity(Errors.fromJson(error))(
      JsonInstances.jsonEncoderOf[F, Errors]
    )
}

class InvalidRequest(val req: Request[IO], val errors: Errors)
  extends Exception(s"Invalid request to '${req.uri}'. ${errors.message}.") {
  def message = getMessage
}
