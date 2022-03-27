package com.malliina.http

import com.malliina.boat.http.CSRFConf
import io.circe.*
import io.circe.parser.{decode, parse}
import io.circe.syntax.EncoderOps
import org.scalajs.dom
import org.scalajs.dom.*

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.Thenable.Implicits.thenable2future

object HttpClient extends HttpClient

class HttpClient extends CSRFConf:
  def get[R: Decoder](uri: String): Future[R] =
    fetch(HttpMethod.GET, uri).flatMap { xhr =>
      validate[R](uri, xhr)
    }

  def post[W: Encoder, R: Decoder](uri: String, data: W): Future[R] =
    makeAjax(HttpMethod.POST, uri, data)

  def patch[W: Encoder, R: Decoder](uri: String, data: W): Future[R] =
    makeAjax(HttpMethod.PATCH, uri, data)

  def put[W: Encoder, R: Decoder](uri: String, data: W): Future[R] =
    makeAjax(HttpMethod.PUT, uri, data)

  private def makeAjax[W: Encoder, R: Decoder](
    method: HttpMethod,
    uri: String,
    data: W
  ): Future[R] =
    val headers = Map(
      "Content-Type" -> "application/json",
      CsrfHeaderName -> CsrfTokenNoCheck
    )
    fetch(method, uri, data.asJson.noSpaces, headers = headers).flatMap { res =>
      validate[R](uri, res)
    }

  private def validate[R: Decoder](uri: String, res: dom.Response): Future[R] =
    val status = res.status
    if status >= 200 && status <= 300 then
      res.text().flatMap { str =>
        decode[R](str).fold(
          err => Future.failed(new JsonException(err, res)),
          ok => Future.successful(ok)
        )
      }
    else Future.failed(new StatusException(uri, res))

  def fetch(
    method: HttpMethod,
    url: String,
    data: String | Unit = (),
    headers: Map[String, String] = Map.empty,
    credentials: RequestCredentials = RequestCredentials.include
  ) =
    val req = new RequestInit {}
    req.method = method
    req.body = data
    req.credentials = credentials
    val hs = new Headers()
    headers.map { case (name, value) => hs.append(name, value) }
    req.headers = hs
    dom.fetch(url, req)

class JsonException(val error: io.circe.Error, val res: dom.Response) extends Exception
class StatusException(val uri: String, val res: dom.Response)
  extends Exception(s"Invalid response code '${res.status}' from '$uri'.")
