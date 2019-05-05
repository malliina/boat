package com.malliina.http

import com.malliina.boat.http.CSRFConf
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.XMLHttpRequest
import play.api.libs.json.{JsError, Json, Reads, Writes}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object HttpClient extends HttpClient

class HttpClient extends CSRFConf {
  def get[R: Reads](uri: String): Future[R] =
    Ajax.get(uri).flatMap { xhr =>
      validate[R](uri, xhr)
    }

  def put[W: Writes, R: Reads](uri: String, data: W): Future[R] = {
    val headers = Map(
      "Content-Type" -> "application/json",
      CsrfHeaderName -> CsrfTokenNoCheck
    )
    Ajax.put(uri, Json.stringify(Json.toJson(data)), headers = headers).flatMap { xhr =>
      validate[R](uri, xhr)
    }
  }

  private def validate[R: Reads](uri: String, xhr: XMLHttpRequest) = {
    val status = xhr.status
    val json = Json.parse(xhr.responseText)
    if (status >= 200 && status <= 300) {
      json
        .validate[R]
        .fold(err => Future.failed(new JsonException(JsError(err), xhr)),
              ok => Future.successful(ok))
    } else {
      Future.failed(new StatusException(uri, xhr))
    }
  }
}

class JsonException(val error: JsError, val xhr: XMLHttpRequest) extends Exception
class StatusException(val uri: String, val xhr: XMLHttpRequest)
    extends Exception(s"Invalid response code '${xhr.status}' from '$uri'.")
