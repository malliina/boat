package com.malliina.boat.http4s

import cats.data.NonEmptyList
import cats.effect.{Async, Sync}
import cats.implicits.*
import com.malliina.assets.HashedAssets
import com.malliina.boat.http4s.StaticService.log
import com.malliina.boat.BuildInfo
import com.malliina.util.AppLogger
import com.malliina.values.UnixPath
import org.http4s.CacheDirective.{`max-age`, `no-cache`, `public`, `no-store`, `must-revalidate`}
import org.http4s.headers.`Cache-Control`
import org.http4s.{Header, HttpRoutes, Request, StaticFile}
import org.typelevel.ci.CIStringSyntax

import scala.concurrent.duration.DurationInt

object StaticService:
  private val log = AppLogger(getClass)

class StaticService[F[_]: Async] extends BasicService[F]:
  private val fontExtensions = List(".woff", ".woff2", ".eot", ".ttf")
  private val supportedStaticExtensions =
    List(".html", ".js", ".map", ".css", ".png", ".ico", ".svg", ".map") ++ fontExtensions

  private val publicDir = fs2.io.file.Path(BuildInfo.assetsDir)
  private val allowAllOrigins = Header.Raw(ci"Access-Control-Allow-Origin", "*")

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ GET -> rest if supportedStaticExtensions.exists(rest.toString.endsWith) =>
      val file = UnixPath(rest.segments.mkString("/"))
      val isCacheable =
        (file.value.count(_ == '.') == 2 || file.value.startsWith("static/")) &&
          !file.value.endsWith(".map")
      val cacheHeaders =
        if isCacheable then NonEmptyList.of(`max-age`(365.days), `public`)
        else NonEmptyList.of(`no-cache`(), `no-store`, `must-revalidate`)
      val assetPath: fs2.io.file.Path = publicDir.resolve(file.value)
      val resourcePath = s"${BuildInfo.publicFolder}/${file.value}"
      val path = if BuildInfo.isProd then resourcePath else assetPath.toNioPath.toAbsolutePath
      log.info(s"Searching for '$path'...")
      val search =
        if BuildInfo.isProd then StaticFile.fromResource(resourcePath, Option(req))
        else StaticFile.fromPath(assetPath, Option(req))
      search
        .map(_.putHeaders(`Cache-Control`(cacheHeaders), allowAllOrigins))
        .fold(onNotFound(req))(_.pure[F])
        .flatten
  }

  private def onNotFound(req: Request[F]) =
    Sync[F].delay(log.info(s"Not found '${req.uri}'.")).flatMap { _ =>
      notFoundReq(req)
    }
