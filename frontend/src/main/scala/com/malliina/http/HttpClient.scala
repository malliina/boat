package com.malliina.http

import com.malliina.boat.http.CSRFConf
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.ext.Ajax.InputData
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

  def patch[W: Writes, R: Reads](uri: String, data: W): Future[R] =
    makeAjax("PATCH", uri, data)

  def put[W: Writes, R: Reads](uri: String, data: W): Future[R] =
    makeAjax("PUT", uri, data)

  private def makeAjax[W: Writes, R: Reads](method: String, uri: String, data: W): Future[R] = {
    val headers = Map(
      "Content-Type" -> "application/json",
      CsrfHeaderName -> CsrfTokenNoCheck
    )
    ajax(method, uri, Json.stringify(Json.toJson(data)), headers = headers).flatMap { xhr =>
      validate[R](uri, xhr)
    }
  }

  private def validate[R: Reads](uri: String, xhr: XMLHttpRequest): Future[R] = {
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

  // Modified from Extensions.scala
  def ajax(method: String,
           url: String,
           data: InputData = null,
           timeout: Int = 0,
           headers: Map[String, String] = Map.empty,
           withCredentials: Boolean = false,
           responseType: String = "") = {
    Ajax(method, url, data, timeout, headers, withCredentials, responseType)
  }
}

class JsonException(val error: JsError, val xhr: XMLHttpRequest) extends Exception
class StatusException(val uri: String, val xhr: XMLHttpRequest)
    extends Exception(s"Invalid response code '${xhr.status}' from '$uri'.")
