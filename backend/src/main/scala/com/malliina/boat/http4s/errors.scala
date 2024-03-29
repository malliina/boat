package com.malliina.boat.http4s

import com.malliina.boat.Errors
import io.circe.{DecodingFailure, Json}
import org.http4s.*

class JsonException(val error: DecodingFailure, val json: Json) extends DecodeFailure:
  val errors = Errors.fromJson(error)
  val detailed = errors.errors
    .filter(_.message.message.nonEmpty)
    .map(e => e.message)
    .mkString(", ")
  override def message: String = s"JSON exception for '$json'. $detailed"
  override def cause: Option[Throwable] = None
  override def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F] =
    Response(Status.BadRequest, httpVersion).withEntity(Errors.fromJson(error))(
      JsonInstances.jsonEncoderOf[F, Errors]
    )

class InvalidRequest(val req: Request[?], val errors: Errors)
  extends Exception(s"Invalid request to '${req.uri}'. ${errors.message}."):
  def message = getMessage
