package com.malliina.boat.http4s

import com.malliina.http.FullUrl
import org.http4s.Request
import org.http4s.headers.Host
import org.typelevel.ci.CIStringSyntax

object Urls:
  def hostOnly[F[_]](req: Request[F]): FullUrl =
    val proto = if isSecure(req) then "https" else "http"
    val uri = req.uri
    val cloudHost = req.headers.get(ci"X-Forwarded-Host").map(_.head.value)
    val directHost = req.headers
      .get[Host]
      .map(hp => hp.port.fold(hp.host)(port => s"${hp.host}:$port"))
    val hostAndPort =
      cloudHost.orElse(uri.host.map(_.value)).orElse(directHost).getOrElse("localhost")
    FullUrl(proto, hostAndPort, "")

  def isSecure[F[_]](req: Request[F]): Boolean =
    req.isSecure.getOrElse(false) || req.headers
      .get(ci"X-Forwarded-Proto")
      .exists(_.head.value == "https")

  def address[F[_]](req: Request[F]): String =
    req.headers
      .get(ci"X-Forwarded-For")
      .map(_.head.value)
      .orElse(req.remoteAddr.map(_.toUriString))
      .getOrElse("unknown")
