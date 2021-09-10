package com.malliina.http

import com.malliina.boat.http.CSRFConf
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.ext.Ajax.InputData
import org.scalajs.dom.raw.XMLHttpRequest

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import io.circe.*
import io.circe.syntax.EncoderOps
import io.circe.parser.{decode, parse}

object HttpClient extends HttpClient

class HttpClient extends CSRFConf:
  def get[R: Decoder](uri: String): Future[R] =
    Ajax.get(uri).flatMap { xhr =>
      validate[R](uri, xhr)
    }

  def post[W: Encoder, R: Decoder](uri: String, data: W): Future[R] =
    makeAjax("POST", uri, data)

  def patch[W: Encoder, R: Decoder](uri: String, data: W): Future[R] =
    makeAjax("PATCH", uri, data)

  def put[W: Encoder, R: Decoder](uri: String, data: W): Future[R] =
    makeAjax("PUT", uri, data)

  private def makeAjax[W: Encoder, R: Decoder](method: String, uri: String, data: W): Future[R] =
    val headers = Map(
      "Content-Type" -> "application/json",
      CsrfHeaderName -> CsrfTokenNoCheck
    )
    ajax(method, uri, data.asJson.noSpaces, headers = headers).flatMap { xhr =>
      validate[R](uri, xhr)
    }

  private def validate[R: Decoder](uri: String, xhr: XMLHttpRequest): Future[R] =
    val status = xhr.status
    if status >= 200 && status <= 300 then
      decode[R](xhr.responseText)
        .fold(
          err => Future.failed(new JsonException(err, xhr)),
          ok => Future.successful(ok)
        )
    else Future.failed(new StatusException(uri, xhr))

  // Modified from Extensions.scala
  def ajax(
    method: String,
    url: String,
    data: InputData = null,
    timeout: Int = 0,
    headers: Map[String, String] = Map.empty,
    withCredentials: Boolean = false,
    responseType: String = ""
  ) =
    Ajax(method, url, data, timeout, headers, withCredentials, responseType)

class JsonException(val error: io.circe.Error, val xhr: XMLHttpRequest) extends Exception
class StatusException(val uri: String, val xhr: XMLHttpRequest)
  extends Exception(s"Invalid response code '${xhr.status}' from '$uri'.")
