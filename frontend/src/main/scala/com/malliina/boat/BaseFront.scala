package com.malliina.boat

import org.scalajs.dom.{Element, HTMLAnchorElement, window}

import java.net.URI
import scala.util.Try

trait BaseFront extends FrontKeys:
  val document = org.scalajs.dom.document
  val href = new URI(window.location.href)
  private val queryParams = QueryString.parse

  def parseUri: PathState = href.getPath.split('/').toList match
    case _ :: "tracks" :: track :: _ => PathState.Name(TrackName.unsafe(track))
    case _ :: "routes" :: srcLat :: srcLng :: destLat :: destLng :: _ =>
      val result = for
        srcLatD <- toDouble(srcLat)
        srcLngD <- toDouble(srcLng)
        destLatD <- toDouble(destLat)
        destLngD <- toDouble(destLng)
      yield RouteRequest(srcLatD, srcLngD, destLatD, destLngD)
      result
        .map(e => e.fold(_ => PathState.NoTrack, req => PathState.Route(req)))
        .getOrElse(PathState.NoTrack)
    case _ :: canonical :: Nil => PathState.Canonical(TrackCanonical.unsafe(canonical))
    case _                     => PathState.NoTrack

  private def toDouble(s: String) = Try(s.toDouble).toOption
  def queryInt(key: String) = query(key).flatMap(s => Try(s.toInt).toOption)
  def query(key: String) = queryParams.get(key)
  def anchor(id: String) = elemAs[HTMLAnchorElement](id)
  def elemAsGet[T <: Element](id: String) =
    elemAs[T](id).fold(nf => throw Exception(s"Not found: '$nf'."), identity)
  def elemAs[T <: Element](id: String) = elem(id).map(_.asInstanceOf[T])

  def elemGet(id: String) = elem(id).fold(nf => throw Exception(s"Not found: '$nf'."), identity)
  def elem(id: String): Either[NotFound, Element] =
    Option(document.getElementById(id)).toRight(NotFound(id))

  def elemsByClass[T](cls: String): List[T] =
    document.getElementsByClassName(cls).toList.map(_.asInstanceOf[T])

enum PathState:
  case Canonical(track: TrackCanonical)
  case Name(track: TrackName)
  case Route(req: RouteRequest)
  case NoTrack

  def toOption: Option[TrackName] = this match
    case Name(track) => Option(track)
    case _           => None

case class NotFound(id: String) extends AnyVal:
  override def toString: String = id
