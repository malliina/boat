package com.malliina.boat.http4s

import cats.data.NonEmptyList
import cats.effect.{Blocker, ContextShift, Sync}
import cats.implicits._
import com.malliina.assets.HashedAssets
import com.malliina.boat.http4s.StaticService.log
import com.malliina.util.AppLogger
import com.malliina.values.UnixPath
import org.http4s.CacheDirective.{`max-age`, `no-cache`, `public`}
import org.http4s.headers.`Cache-Control`
import org.http4s.{HttpRoutes, Request, StaticFile}

import scala.concurrent.duration.DurationInt

object StaticService {
  private val log = AppLogger(getClass)

  def apply[F[_]](blocker: Blocker, contextShift: ContextShift[F])(implicit
    s: Sync[F]
  ): StaticService[F] =
    new StaticService[F](blocker)(contextShift, s)
}

class StaticService[F[_]](blocker: Blocker)(implicit cs: ContextShift[F], s: Sync[F])
  extends BasicService[F] {
  val fontExtensions = List(".woff", ".woff2", ".eot", ".ttf")
  val supportedStaticExtensions =
    List(".html", ".js", ".map", ".css", ".png", ".ico", ".svg", ".map") ++ fontExtensions

  val prefix = HashedAssets.prefix
  //  val routes = resourceService[F](ResourceService.Config("/db", blocker))
  //  val routes = fileService(FileService.Config("./public", blocker))
  val routes = HttpRoutes.of[F] {
    case req @ GET -> rest if supportedStaticExtensions.exists(rest.toString.endsWith) =>
      val file = UnixPath(rest.segments.mkString("/"))
      val isCacheable = file.value.count(_ == '.') == 2 && !file.value.endsWith(".map")
      val cacheHeaders =
        if (isCacheable) NonEmptyList.of(`max-age`(365.days), `public`)
        else NonEmptyList.of(`no-cache`())
      val res = s"/$prefix/$file"
      log.debug(s"Searching for '$file' at resource '$res'...")
      StaticFile
        .fromResource(res, blocker, Option(req))
        .map(_.putHeaders(`Cache-Control`(cacheHeaders)))
        .fold(onNotFound(req))(_.pure[F])
        .flatten
  }

  private def onNotFound(req: Request[F]) = {
    Sync[F].delay(log.info(s"Not found '${req.uri}'.")).flatMap { _ =>
      notFoundReq(req)
    }
  }
}
