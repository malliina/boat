package com.malliina.boat

import java.net.URI

import org.scalajs.dom.raw.Element
import org.scalajs.dom.window

import scala.util.Try

trait BaseFront extends FrontKeys {
  val document = org.scalajs.dom.document
  val href = new URI(window.location.href)
  val queryString = Option(href.getQuery).getOrElse("")
  val queryParams: Map[String, List[String]] = queryString.split("&").toList
    .map { kv => kv.split("=").toList }
    .collect { case key :: value :: Nil => key -> value }
    .groupBy { case (key, _) => key }
    .mapValues { vs => vs.map { case (_, v) => v } }

  def readTrack: Either[NotFound, TrackName] = href.getPath.split('/').toList match {
    case _ :: "tracks" :: track :: _ => Right(TrackName(track))
    case _ => Left(NotFound("track"))
  }

  def queryDouble(key: String) = query(key).flatMap(s => Try(s.toDouble).toOption)

  def queryInt(key: String) = query(key).flatMap(s => Try(s.toInt).toOption)

  def query(key: String) = queryParams.get(key).flatMap(_.headOption)

  def elemAs[T](id: String) = elem(id).map(_.asInstanceOf[T])

  def elem(id: String): Either[NotFound, Element] = Option(document.getElementById(id)).toRight(NotFound(id))
}

case class NotFound(id: String) {
  override def toString: String = id
}
