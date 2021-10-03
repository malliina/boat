package com.malliina.boat.http4s

import cats.Functor
import cats.data.Kleisli
import org.http4s.{Header, Headers, Response}
import org.typelevel.ci.CIStringSyntax

object CSP:
  val csps = Seq(
    "default-src 'self' 'unsafe-inline' *.mapbox.com",
    "font-src 'self' data: https://fonts.gstatic.com https://use.fontawesome.com",
    "style-src 'self' 'unsafe-inline' https://maxcdn.bootstrapcdn.com https://fonts.googleapis.com *.mapbox.com https://use.fontawesome.com",
    "connect-src * https://*.tiles.mapbox.com https://api.mapbox.com",
    "img-src 'self' data: blob:",
    "child-src blob:",
    "script-src 'unsafe-eval' 'self' *.mapbox.com npmcdn.com https://cdnjs.cloudflare.com"
  )
  val headerValue = csps.mkString("; ")
  val header = Headers(Header.Raw(ci"Content-Security-Policy", headerValue))

  // Check e.g. HSTS.scala for syntax inspiration
  def apply[F[_]: Functor, A, G[_]](http: Kleisli[F, A, Response[G]]): Kleisli[F, A, Response[G]] =
    Kleisli { req =>
      http.map(_.putHeaders(header)).apply(req)
    }
