package com.malliina.boat.parking

import cats.effect.{Async, Sync}
import com.malliina.boat.parking.Parking.ParkingCapacity
import com.malliina.boat.{FeatureCollection, Feature, LocalConf, Resources}
import com.malliina.http.FullUrl
import com.malliina.http.UrlSyntax.https
import com.malliina.http.io.HttpClientF2
import io.circe.{Codec, Json}
import io.circe.parser.decode
import cats.syntax.all.{toFlatMapOps, toFunctorOps}

import java.nio.file.Files

object Parking extends Resources:
  private val localFile = LocalConf.appDir.resolve("parking-areas.json")

  private def parkingFile = Resources.file("parking-areas.json", localFile)

  def load[F[_]: Sync]: F[Json] =
    val F = Sync[F]
    F.rethrow(F.delay(decode[Json](Files.readString(parkingFile))))

  case class ParkingCapacity(next: Option[FullUrl], features: Seq[Feature]) derives Codec.AsObject

class Parking[F[_]: Async](http: HttpClientF2[F]):
  private val firstPage: FullUrl = https"pubapi.parkkiopas.fi/public/v1/parking_area/?format=json"

  def capacity(): F[FeatureCollection] = capacities(firstPage, Nil).map: cs =>
    FeatureCollection(cs.flatMap(_.features))

  private def capacities(next: FullUrl, acc: Seq[ParkingCapacity] = Nil): F[Seq[ParkingCapacity]] =
    http
      .getAs[ParkingCapacity](next)
      .flatMap: page =>
        val collected = acc :+ page
        page.next
          .map: nextPage =>
            capacities(nextPage, collected)
          .getOrElse:
            Async[F].pure(collected)
