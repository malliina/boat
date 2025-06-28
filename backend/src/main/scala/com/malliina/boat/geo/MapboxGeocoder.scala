package com.malliina.boat.geo

import cats.effect.Async
import cats.effect.kernel.Outcome.Succeeded
import cats.effect.std.Semaphore
import cats.effect.syntax.all.{genSpawnOps, genTemporalOps_, monadCancelOps, monadCancelOps_}
import cats.implicits.{toFlatMapOps, toFunctorOps}
import com.malliina.boat.Coord
import com.malliina.boat.geo.MapboxGeocoder.ReverseGeocodeResponse
import com.malliina.boat.geo.RateLimiter.LimitExceeded
import com.malliina.boat.geo.{MapboxGeocoder, ReverseGeocode}
import com.malliina.http.HttpClient
import com.malliina.http.UrlSyntax.https
import com.malliina.values.AccessToken
import io.circe.Codec

import scala.concurrent.duration.{DurationLong, FiniteDuration}

object MapboxGeocoder:

  case class GeocodeProperties(
    name: Option[String],
    name_preferred: Option[String],
    full_address: Option[String]
  ) derives Codec.AsObject:
    def address = name_preferred.orElse(name).orElse(full_address)

  case class GeocodeFeature(properties: GeocodeProperties) derives Codec.AsObject
  case class ReverseGeocodeResponse(features: List[GeocodeFeature]) derives Codec.AsObject

class MapboxGeocoder[F[_]: Async](token: AccessToken, http: HttpClient[F]) extends Geocoder[F]:
  val baseUrl = https"api.mapbox.com/search/geocode/v6/reverse"

  def reverseGeocode(coord: Coord): F[Option[ReverseGeocode]] =
    val url = baseUrl.withQuery(
      "longitude" -> s"${coord.lng}",
      "latitude" -> s"${coord.lat}",
      "access_token" -> token.token
    )
    http
      .getAs[ReverseGeocodeResponse](url)
      .map: res =>
        res.features.headOption
          .flatMap(_.properties.address)
          .map: address =>
            ReverseGeocode(address)

object ThrottlingGeocoder:
  def default[F[_]: Async](
    token: AccessToken,
    http: HttpClient[F]
  ): F[ThrottlingGeocoder[F]] =
    RateLimiter
      .default[F]()
      .map: limiter =>
        ThrottlingGeocoder(limiter, MapboxGeocoder(token, http))

class ThrottlingGeocoder[F[_]: Async](limiter: RateLimiter[F], geocoder: Geocoder[F])
  extends Geocoder[F]:
  override def reverseGeocode(coord: Coord): F[Option[ReverseGeocode]] =
    limiter.submit(geocoder.reverseGeocode(coord)).map(_.flatten)

object RateLimiter:
  class LimitExceeded(val window: FiniteDuration)
    extends Exception(s"Rate limit exceeded over window of $window.")

  /** Mapbox free tier includes 100000 temporary reverse geocoding requests per month, that is 2.28
    * requests/minute or 139 requests/hour. So, we limit the request frequency to 100 requests per
    * hour to stay within the free tier limits.
    */
  def default[F[_]: Async](tasks: Int = 100, window: FiniteDuration = 1.hour): F[RateLimiter[F]] =
    Semaphore[F](tasks).map: sem =>
      RateLimiter(window, sem)

class RateLimiter[F[_]: Async](window: FiniteDuration, semaphore: Semaphore[F]):
  private val F = Async[F]

  def submit[T](task: F[T]): F[Option[T]] =
    tryAcquireReleaseLater.flatMap: acquired =>
      if acquired then task.map(t => Option(t))
      else F.pure(None)

  def submitOrFail[T](task: F[T]): F[T] =
    submit(task).flatMap: opt =>
      opt.map(t => F.pure(t)).getOrElse(F.raiseError(LimitExceeded(window)))

  private def tryAcquireReleaseLater: F[Boolean] = semaphore.tryAcquire.guaranteeCase:
    case Succeeded(out) =>
      out.flatMap: acquired =>
        if acquired then semaphore.release.delayBy(window).start.uncancelable.void
        else F.unit
    case _ => F.unit
