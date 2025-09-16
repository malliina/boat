package com.malliina.boat.parking

import cats.effect.{Async, Sync}
import cats.syntax.all.{toFlatMapOps, toFunctorOps, toTraverseOps}
import com.malliina.boat.geo.Geocoder
import com.malliina.boat.http.Near
import com.malliina.boat.{CapacityProps, Earth, FeatureCollection, LocalConf, MultiPolygon, NearestCoord, ParkingCapacity, ParkingDirections, Polygon, Resources}
import com.malliina.http.{FullUrl, HttpClient}
import com.malliina.http.UrlSyntax.https
import io.circe.parser.decode
import io.circe.Json

import java.nio.file.Files

object Parking extends Resources:
  private val localFile = LocalConf.appDir.resolve("parking-areas.json")

  private def parkingFile = Resources.file("parking-areas.json", localFile)

  def load[F[_]: Sync]: F[Json] =
    val F = Sync[F]
    F.rethrow(F.blocking(decode[Json](Files.readString(parkingFile))))

class Parking[F[_]: Async](http: HttpClient[F], geo: Geocoder[F]):
  private val firstPage: FullUrl = https"pubapi.parkkiopas.fi/public/v1/parking_area/?format=json"

  def near(query: Near): F[Seq[ParkingDirections]] =
    val coord = query.coord
    capacity().flatMap: fc =>
      val withoutGeocoding = fc.features
        .flatMap: f =>
          val areas = f.geometry match
            case MultiPolygon(_, coordinates) => coordinates.flatten
            case Polygon(_, coordinates)      => coordinates
            case _                            => Nil
          val capacity = f.props.as[CapacityProps].toOption.flatMap(_.capacityEstimate).getOrElse(0)
          areas
            .filter(_ => capacity > 0)
            .flatMap: area =>
              area
                .map(c => NearestCoord(c, Earth.distance(coord, c), None))
                .filter(n => n.distance < query.radius)
                .minByOption(_.distance)
                .map: nearest =>
                  ParkingDirections(coord, area, nearest, capacity)
        .sortBy(pd => pd.nearest.distance)
        .take(query.limit)
      withoutGeocoding.traverse: pd =>
        geo
          .reverseGeocode(pd.nearest.coord)
          .map: opt =>
            pd.withAddress(opt.map(_.address))

  def capacity(): F[FeatureCollection] = capacities(firstPage, Nil).map: cs =>
    FeatureCollection(cs.flatMap(_.features))

  private def capacities(next: FullUrl, acc: Seq[ParkingCapacity]): F[Seq[ParkingCapacity]] =
    http
      .getAs[ParkingCapacity](next)
      .flatMap: page =>
        val collected = acc :+ page
        page.next
          .map: nextPage =>
            capacities(nextPage, collected)
          .getOrElse:
            Async[F].pure(collected)
