package com.malliina.boat.http4s

import cats.Functor
import cats.data.Kleisli
import org.http4s.{Header, Headers, Response}
import org.typelevel.ci.CIStringSyntax

object CSP:
  private val csps = Seq(
    "default-src 'self' 'unsafe-inline' *.mapbox.com https://cdn.boat-tracker.com",
    "font-src 'self' data: https://fonts.gstatic.com https://use.fontawesome.com https://cdn.boat-tracker.com",
    "style-src 'self' 'unsafe-inline' https://maxcdn.bootstrapcdn.com https://fonts.googleapis.com *.mapbox.com https://use.fontawesome.com https://cdn.boat-tracker.com",
    "connect-src * https://*.tiles.mapbox.com https://api.mapbox.com",
    "img-src 'self' data: blob: https://cdn.boat-tracker.com",
    "child-src blob:",
    "script-src 'unsafe-eval' 'self' *.mapbox.com npmcdn.com https://cdnjs.cloudflare.com https://cdn.boat-tracker.com"
  )
  private val headerValue = csps.mkString("; ")
  val header = Headers(Header.Raw(ci"Content-Security-Policy", headerValue))

  // Check e.g. HSTS.scala for syntax inspiration
  def apply[F[_]: Functor, A, G[_]](http: Kleisli[F, A, Response[G]]): Kleisli[F, A, Response[G]] =
    Kleisli { req =>
      http.map(_.putHeaders(header)).apply(req)
    }

  def when[F[_]: Functor, A, G[_]](
    isProd: Boolean
  )(http: Kleisli[F, A, Response[G]]): Kleisli[F, A, Response[G]] =
    if isProd then apply(http)
    else http
