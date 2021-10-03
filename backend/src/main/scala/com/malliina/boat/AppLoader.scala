package com.malliina.boat

import cats.effect.IO
import com.malliina.boat.auth.{EmailAuth, TokenEmailAuth}
import com.malliina.boat.db.Conf
import com.malliina.boat.push.{BoatPushService, PushEndpoint}
import com.malliina.http.HttpClient

import java.nio.file.Paths

object LocalConf2:
  val localConfFile = Paths.get(sys.props("user.home")).resolve(".boat/boat.conf")

//  override lazy val csrfConfig = CSRFConfig(
//    tokenName = CsrfTokenName,
//    cookieName = Option(CsrfCookieName),
//    headerName = CsrfHeaderName,
//    shouldProtect = rh => !rh.headers.get(CsrfHeaderName).contains(CsrfTokenNoCheck)
//  )
//
//  override def httpFilters: Seq[EssentialFilter] =
//    Seq(new GzipFilter(), csrfFilter, securityHeadersFilter)

//  val csps = Seq(
//    "default-src 'self' 'unsafe-inline' *.mapbox.com",
//    "font-src 'self' data: https://fonts.gstatic.com https://use.fontawesome.com",
//    "style-src 'self' 'unsafe-inline' https://maxcdn.bootstrapcdn.com https://fonts.googleapis.com *.mapbox.com https://use.fontawesome.com",
//    "connect-src * https://*.tiles.mapbox.com https://api.mapbox.com",
//    "img-src 'self' data: blob:",
//    "child-src blob:",
//    "script-src 'unsafe-eval' 'self' *.mapbox.com npmcdn.com https://cdnjs.cloudflare.com"
//  )
//  val csp = csps mkString "; "

//  // Sets sameSite = None, otherwise the Google auth redirect will wipe out the session state
//  override lazy val httpConfiguration =
//    defaultHttpConf.copy(
//      session = defaultHttpConf.session
//        .copy(cookieName = "boatSession", sameSite = None)
//    )
