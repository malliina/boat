package com.malliina.boat

import cats.effect.Async
import cats.implicits.toFunctorOps
import com.malliina.boat.GoogleGeocoder.AddressType.StreetAddress
import com.malliina.boat.GoogleGeocoder.GeocoderResults
import com.malliina.http.HttpClient
import com.malliina.http.UrlSyntax.https
import com.malliina.values.StringEnumCompanion
import io.circe.Codec

object GoogleGeocoder:
  enum AddressType(val name: String):
    case StreetAddress extends AddressType("street_address")
    case Other(n: String) extends AddressType(n)
  object AddressType extends StringEnumCompanion[AddressType]:
    override def all: Seq[AddressType] = Seq(StreetAddress)
    override def write(t: AddressType): String = t.name
  case class Address(formatted_address: String, types: Seq[AddressType]) derives Codec.AsObject
  case class GeocoderResults(results: Seq[Address]) derives Codec.AsObject

class GoogleGeocoder[F[_]: Async](token: AccessToken, http: HttpClient[F]) extends Geocoder[F]:
  val baseUrl = https"maps.googleapis.com/maps/api/geocode/json"

  def reverseGeocode(coord: Coord): F[Option[ReverseGeocode]] =
    val url = baseUrl.withQuery(
      "latlng" -> s"${coord.lat},${coord.lng}",
      "key" -> token.value,
      "result_type" -> "street_address"
    )
    http
      .getAs[GeocoderResults](url)
      .map: res =>
        res.results
          .find(_.types.contains(StreetAddress))
          .map: addr =>
            ReverseGeocode(addr.formatted_address)
