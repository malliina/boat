package com.malliina.boat.http4s

import com.malliina.http.FullUrl
import org.http4s.Request
import org.http4s.headers.Host
import org.http4s.util.CaseInsensitiveString

object Urls {
  def hostOnly[F[_]](req: Request[F]): FullUrl = {
    val proto = if (isSecure(req)) "https" else "http"
    val uri = req.uri
    val hostAndPort =
      req.headers.get(Host).map(_.value).getOrElse("localhost")
    FullUrl(proto, uri.host.map(_.value).getOrElse(hostAndPort), "")
  }

  def isSecure[F[_]](req: Request[F]): Boolean =
    req.isSecure.getOrElse(false) || req.headers
      .get(CaseInsensitiveString("X-Forwarded-Proto"))
      .exists(_.value == "https")

  def address[F[_]](req: Request[F]): String =
    req.headers
      .get(CaseInsensitiveString("X-Forwarded-For"))
      .map(_.value)
      .orElse(req.remoteAddr)
      .getOrElse("unknown")
}
