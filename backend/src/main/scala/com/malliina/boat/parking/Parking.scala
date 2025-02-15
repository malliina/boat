package com.malliina.boat.parking

import cats.effect.{Async, Sync}
import cats.syntax.all.{toFlatMapOps, toFunctorOps}
import com.malliina.boat.parking.Parking.{CapacityProps, NearestCoord, ParkingCapacity, ParkingDirections}
import com.malliina.boat.{Coord, Earth, Feature, FeatureCollection, LocalConf, MultiPolygon, Polygon, Resources}
import com.malliina.http.FullUrl
import com.malliina.http.UrlSyntax.https
import com.malliina.http.io.HttpClientF2
import io.circe.parser.decode
import io.circe.{Codec, Decoder, Json}
import com.malliina.measure.{DistanceIntM, DistanceM}

import java.nio.file.Files

object Parking extends Resources:
  private val localFile = LocalConf.appDir.resolve("parking-areas.json")

  private def parkingFile = Resources.file("parking-areas.json", localFile)

  def load[F[_]: Sync]: F[Json] =
    val F = Sync[F]
    F.rethrow(F.blocking(decode[Json](Files.readString(parkingFile))))

  case class ParkingCapacity(next: Option[FullUrl], features: Seq[Feature]) derives Codec.AsObject

  case class CapacityProps(capacityEstimate: Option[Int])

  object CapacityProps:
    private case class CapacityPropsJson(capacity_estimate: Option[Int]) derives Codec.AsObject

    given Decoder[CapacityProps] = Decoder[CapacityPropsJson].map: json =>
      CapacityProps(json.capacity_estimate)

  case class NearestCoord(coord: Coord, distance: DistanceM) derives Codec.AsObject

  case class ParkingDirections(from: Coord, to: Seq[Coord], nearest: NearestCoord, capacity: Int)
    derives Codec.AsObject

  case class ParkingResponse(directions: Seq[ParkingDirections]) derives Codec.AsObject

class Parking[F[_]: Async](http: HttpClientF2[F]):
  private val firstPage: FullUrl = https"pubapi.parkkiopas.fi/public/v1/parking_area/?format=json"

  def near(coord: Coord, radius: DistanceM = 300.meters): F[Seq[ParkingDirections]] =
    capacity().map: fc =>
      fc.features
        .flatMap: f =>
          val areas = f.geometry match
            case MultiPolygon(tpe, coordinates) => coordinates.flatten
            case Polygon(tpe, coordinates)      => coordinates
            case _                              => Nil
          val capacity = f.props.as[CapacityProps].toOption.flatMap(_.capacityEstimate).getOrElse(0)
          areas
            .filter(_ => capacity > 0)
            .flatMap: area =>
              area
                .map(c => NearestCoord(c, Earth.distance(coord, c)))
                .filter(n => n.distance < radius)
                .minByOption(_.distance)
                .map: nearest =>
                  ParkingDirections(coord, area, nearest, capacity)
        .sortBy(pd => pd.nearest.distance)

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
