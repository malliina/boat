package com.malliina.boat.geo

import cats.effect.Async
import cats.implicits.toFlatMapOps
import cats.syntax.all.toFunctorOps
import com.malliina.boat.geo.MapboxImages.Conf
import com.malliina.boat.MapConf
import com.malliina.geo.Coord
import com.malliina.http.HttpClient
import com.malliina.http.UrlSyntax.https
import com.malliina.values.AccessToken

import java.nio.file.Files
import scala.concurrent.duration.DurationInt

object MapboxImages:
  case class Conf(token: AccessToken, styleId: String, username: String)

  def default[F[_]: Async](token: AccessToken, http: HttpClient[F]) =
    RateLimiter
      .default[F](720, 12.hours)
      .map: limiter =>
        val images = MapboxImages(Conf(token, MapConf.active.styleId, "skogberglabs"), http)
        ThrottlingImages[F](limiter, images)

  class ThrottlingImages[F[_]: Async](limiter: RateLimiter[F], images: ImageApi[F])
    extends ImageApi[F]:
    val F = Async[F]

    override def image(coord: Coord, size: Size): F[Array[Byte]] =
      limiter.submitOrFail(images.image(coord, size))

class MapboxImages[F[_]: Async](conf: Conf, http: HttpClient[F]) extends ImageApi[F]:
  val F = Async[F]
  val baseUrl = https"api.mapbox.com/styles/v1" / conf.username / conf.styleId / "static"

  private val bearing = 0
  private val pitch = 60
  private val zoom = 10.25

  override def image(coord: Coord, size: Size): F[Array[Byte]] =
    val segment = Seq(s"${coord.lng}", s"${coord.lat}", zoom, bearing, pitch)
    val plainUrl = baseUrl / segment.mkString(",") / size.wxh
    val url = plainUrl.withQuery("access_token" -> conf.token.token)
    val to = Files.createTempFile("mapbox", "bytes")
    http
      .download(url, to)
      .flatMap: res =>
        res.fold(
          err => F.raiseError(err.toException),
          _ => F.blocking(Files.readAllBytes(to))
        )
