package com.malliina.boat.geo

import cats.Applicative
import com.malliina.geo.Coord
import io.circe.Codec

case class ReverseGeocode(address: String) derives Codec.AsObject

trait Geocoder[F[_]]:
  def reverseGeocode(coord: Coord): F[Option[ReverseGeocode]]

object Geocoder:
  def noop[F[_]: Applicative] = new Geocoder[F]:
    override def reverseGeocode(coord: Coord): F[Option[ReverseGeocode]] = Applicative[F].pure(None)
