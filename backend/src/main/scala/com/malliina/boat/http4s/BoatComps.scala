package com.malliina.boat.http4s

import com.malliina.boat.cars.PolestarService
import com.malliina.boat.db.*
import com.malliina.boat.geo.{Geocoder, ImageApi}
import com.malliina.boat.html.BoatHtml
import com.malliina.boat.parking.Parking
import com.malliina.boat.push.PushService
import com.malliina.boat.S3Client
import com.malliina.values.AccessToken

case class BoatComps[F[_]](
  boatHtml: BoatHtml,
  carHtml: BoatHtml,
  db: TracksSource[F],
  vessels: VesselDatabase[F],
  inserts: TrackInsertsDatabase[F],
  stats: StatsSource[F],
  auth: AuthService[F],
  mapboxToken: AccessToken,
  s3: S3Client[F],
  push: PushService[F],
  streams: BoatStreams[F],
  mapbox: Geocoder[F],
  images: ImageApi[F],
  parking: Parking[F],
  polestar: PolestarService[F],
  slowly: Slowly[F]
)
