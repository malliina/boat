package com.malliina.boat

import java.net.URI

import com.malliina.boat.http.CSRFConf
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.{Element, HTMLAnchorElement}
import org.scalajs.dom.window
import play.api.libs.json.{Json, Reads, Writes}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Try

trait BaseFront extends FrontKeys with CSRFConf {
  val document = org.scalajs.dom.document
  val href = new URI(window.location.href)
  val queryString = Option(href.getQuery).getOrElse("")
  val queryParams: Map[String, List[String]] = queryString.split("&").toList
    .map { kv => kv.split("=").toList }
    .collect { case key :: value :: Nil => key -> value }
    .groupBy { case (key, _) => key }
    .mapValues { vs => vs.map { case (_, v) => v } }

  def put[W: Writes, R: Reads](uri: String, data: W): Future[R] = {
    val headers = Map(
      "Content-Type" -> "application/json",
      CsrfHeaderName -> CsrfTokenNoCheck
    )
    Ajax.put(uri, Json.stringify(Json.toJson(data)), headers = headers).flatMap { xhr =>
      val status = xhr.status
      val json = Json.parse(xhr.responseText)
      if (status >= 200 && status <= 300) Future.successful(json.as[R])
      else Future.failed(new Exception(s"Invalid response code '$status' from '$uri'."))
    }
  }

  def readTrack: Either[NotFound, TrackName] = href.getPath.split('/').toList match {
    case _ :: "tracks" :: track :: _ => Right(TrackName(track))
    case _ => Left(NotFound("track"))
  }

  def queryDouble(key: String) = query(key).flatMap(s => Try(s.toDouble).toOption)

  def queryInt(key: String) = query(key).flatMap(s => Try(s.toInt).toOption)

  def query(key: String) = queryParams.get(key).flatMap(_.headOption)

  def anchor(id: String) = elemAs[HTMLAnchorElement](id)

  def elemAs[T](id: String) = elem(id).map(_.asInstanceOf[T])

  def elem(id: String): Either[NotFound, Element] = Option(document.getElementById(id)).toRight(NotFound(id))
}

case class NotFound(id: String) {
  override def toString: String = id
}
