package com.malliina.boat

import cats.effect.Async
import cats.implicits.toFunctorOps
import com.malliina.boat.MapboxClient.{ReverseGeocode, ReverseGeocodeResponse}
import com.malliina.http.HttpClient
import com.malliina.http.UrlSyntax.https
import io.circe.Codec

object MapboxClient:
  case class ReverseGeocode(address: String) derives Codec.AsObject

  case class GeocodeProperties(
    name: Option[String],
    name_preferred: Option[String],
    full_address: Option[String]
  ) derives Codec.AsObject:
    def address = name_preferred.orElse(name).orElse(full_address)

  case class GeocodeFeature(properties: GeocodeProperties) derives Codec.AsObject
  case class ReverseGeocodeResponse(features: List[GeocodeFeature]) derives Codec.AsObject

class MapboxClient[F[_]: Async](token: AccessToken, http: HttpClient[F]):
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