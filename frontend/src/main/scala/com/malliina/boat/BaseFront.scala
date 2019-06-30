package com.malliina.boat

import java.net.URI

import org.scalajs.dom.raw.{Element, HTMLAnchorElement}
import org.scalajs.dom.window

import scala.util.Try

trait BaseFront extends FrontKeys {
  val document = org.scalajs.dom.document
  val href = new URI(window.location.href)
  val queryString = Option(href.getQuery).getOrElse("")
  val queryParams: Map[String, List[String]] = queryString
    .split("&")
    .toList
    .map { kv =>
      kv.split("=").toList
    }
    .collect { case key :: value :: Nil => key -> value }
    .groupBy { case (key, _) => key }
    .map {
      case (k, v) =>
        (k, v.map { case (_, vv) => vv })
    }

  def readTrack: PathState = href.getPath.split('/').toList match {
    case _ :: "tracks" :: track :: _ => Name(TrackName(track))
    case _ :: "routes" :: srcLat :: srcLng :: destLat :: destLng :: _ =>
      val result = for {
        srcLatD <- toDouble(srcLat)
        srcLngD <- toDouble(srcLng)
        destLatD <- toDouble(destLat)
        destLngD <- toDouble(destLng)
      } yield RouteRequest(srcLatD, srcLngD, destLatD, destLngD)
      result.map(e => e.fold(_ => NoTrack, req => Route(req))).getOrElse(NoTrack)
    case _ :: canonical :: Nil => Canonical(TrackCanonical(canonical))
    case _                     => NoTrack
  }

  def toDouble(s: String) = Try(s.toDouble).toOption

  def queryDouble(key: String) = query(key).flatMap(s => Try(s.toDouble).toOption)

  def queryInt(key: String) = query(key).flatMap(s => Try(s.toInt).toOption)

  def query(key: String) = queryParams.get(key).flatMap(_.headOption)

  def anchor(id: String) = elemAs[HTMLAnchorElement](id)

  def elemGet[T](id: String) = elemAs[T](id).toOption.get

  def elemAs[T](id: String) = elem(id).map(_.asInstanceOf[T])

  def elem(id: String): Either[NotFound, Element] =
    Option(document.getElementById(id)).toRight(NotFound(id))
}

sealed trait PathState {
  def toOption: Option[TrackName] = this match {
    case Name(track) => Option(track)
    case _           => None
  }
}

case class Canonical(track: TrackCanonical) extends PathState
case class Name(track: TrackName) extends PathState
case class Route(req: RouteRequest) extends PathState
case object NoTrack extends PathState

case class NotFound(id: String) {
  override def toString: String = id
}
