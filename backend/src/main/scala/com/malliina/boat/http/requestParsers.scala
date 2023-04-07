package com.malliina.boat.http

import cats.effect.IO
import cats.implicits.*
import com.malliina.boat.http4s.QueryParsers
import com.malliina.boat.{Constants, Coord, Errors, Latitude, Longitude, Mmsi, RouteRequest, SingleError, TimeFormatter, TrackCanonical, TrackName, VesselName}
import com.malliina.values.{Email, ErrorMessage}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.{Headers, Query, QueryParamDecoder, Request}

import java.time.{Instant, LocalDate}
import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.DurationInt

case class BoatEmailRequest[T](user: Email, query: T, headers: Headers)
  extends BoatRequest[T, Email]

case class AnyBoatRequest[T, U](user: U, query: T, headers: Headers) extends BoatRequest[T, U]

trait BoatRequest[T, U]:
  def user: U
  def query: T
  def headers: Headers

case class UserRequest[F[_], U](user: U, req: Request[F]):
//  def body: B = req.body
  def headers = req.headers

sealed abstract class TrackSort(val name: String) extends Named

object TrackSort extends EnumLike[TrackSort]:
  val key = "sort"
  val default = Recent
  val all: Seq[TrackSort] = Seq(Recent, Points, TopSpeed, Length, Name, Time)

  case object Recent extends TrackSort("recent")
  case object Points extends TrackSort("points")
  case object TopSpeed extends TrackSort("topSpeed")
  case object Length extends TrackSort("length")
  case object Name extends TrackSort("name")
  case object Time extends TrackSort("duration")

sealed abstract class SortOrder(val name: String) extends Named

object SortOrder extends EnumLike[SortOrder]:
  val key = "order"
  val default = Desc
  val all: Seq[SortOrder] = Seq(Asc, Desc)

  case object Asc extends SortOrder("asc")
  case object Desc extends SortOrder("desc")

case class TrackQuery(sort: TrackSort, order: SortOrder, limits: Limits) extends LimitLike:
  override def limit = limits.limit
  override def offset = limits.offset

object TrackQuery:
  def apply(q: Query): Either[Errors, TrackQuery] = withDefault(q)

  def withDefault(
    q: Query,
    defaultLimit: Int = Limits.DefaultLimit
  ): Either[Errors, TrackQuery] =
    for
      sort <- TrackSort(q)
      order <- SortOrder(q)
      limits <- Limits(q, defaultLimit)
    yield TrackQuery(sort, order, limits)

trait Named:
  def name: String
  override def toString: String = name

abstract class EnumLike[T <: Named]:
  def key: String
  def default: T
  def all: Seq[T]

  def apply(q: Query): Either[Errors, T] = apply(key, q, default)

  def apply(key: String, q: Query, default: T): Either[Errors, T] =
    apply(key, q).getOrElse(Right(default))

  def apply(key: String, q: Query): Option[Either[Errors, T]] =
    QueryParsers
      .parseOpt[String](q, key)
      .map { e =>
        e.flatMap(s =>
          all.find(_.name == s).toRight(Errors(SingleError.input(s"Unknown $key value: '$s'.")))
        )
      }

case class VesselQuery(
  names: Seq[VesselName],
  mmsis: Seq[Mmsi],
  time: TimeRange,
  limits: Limits
):
  private def describeNames = if names.isEmpty then "" else s"names ${names.mkString(", ")} "
  private def describeMmsis = if mmsis.isEmpty then "" else s"mmsis ${mmsis.mkString(", ")} "
  private def describeTime = if time.isEmpty then "" else s"time ${time.describe} "
  private def describeLimits = s"limit ${limits.limit} offset ${limits.offset}"
  def describe =
    s"$describeNames$describeMmsis$describeTime$describeLimits"
object VesselQuery:
  implicit val json: Codec[VesselQuery] = deriveCodec[VesselQuery]

  implicit val mmsiDecoder: QueryParamDecoder[Mmsi] =
    QueryParsers.decoder[Mmsi](Mmsi.parse)
  implicit val nameDecoder: QueryParamDecoder[VesselName] =
    QueryParsers.decoder[VesselName](VesselName.build)

  def query(q: Query): Either[Errors, VesselQuery] =
    for
      name <- QueryParsers.list[VesselName](VesselName.Key, q)
      mmsi <- QueryParsers.list[Mmsi](Mmsi.Key, q)
      limits <- Limits(q)
      time <- TimeRange(q)
    yield VesselQuery(name, mmsi, time, limits)

case class CarQuery(limits: Limits, timeRange: TimeRange):
  private def timeDescribe = timeRange.describe
  private def space = if timeDescribe.isEmpty then "" else " "
  def describe = s"${timeRange.describe}${space}with ${limits.describe}"

/** @param tracks
  *   tracks to return
  * @param newest
  *   true to return the newest track if no tracks are specified, false means all tracks are
  *   returned
  */
case class BoatQuery(
  limits: Limits,
  timeRange: TimeRange,
  tracks: Seq[TrackName],
  canonicals: Seq[TrackCanonical],
  route: Option[RouteRequest],
  sample: Option[Int],
  newest: Boolean
):
  def neTracks = tracks.toList.toNel
  def neCanonicals = canonicals.toList.toNel
  def limit = limits.limit
  def offset = limits.offset
  def from = timeRange.from
  def to = timeRange.to

object BoatQuery:
  private val NewestKey = "newest"
  private val SampleKey = "sample"
  private val DefaultSample = Constants.DefaultSample
  implicit val bindTrack: QueryParamDecoder[TrackName] =
    QueryParamDecoder.stringQueryParamDecoder.map(s => TrackName(s))
  implicit val bindCanonical: QueryParamDecoder[TrackCanonical] =
    QueryParamDecoder.stringQueryParamDecoder.map(s => TrackCanonical(s))
  val empty = BoatQuery(Limits(0, 0), TimeRange(None, None), Nil, Nil, None, None, newest = true)

  def tracks(tracks: Seq[TrackName]): BoatQuery =
    BoatQuery(
      Limits.default,
      TimeRange(None, None),
      tracks,
      Nil,
      None,
      Option(DefaultSample),
      newest = false
    )

  def recent(now: Instant): BoatQuery =
    BoatQuery(
      Limits.default,
      TimeRange.recent(now),
      Nil,
      Nil,
      None,
      Option(DefaultSample),
      newest = false
    )

  def apply(q: Query): Either[Errors, BoatQuery] =
    for
      limits <- Limits(q)
      timeRange <- TimeRange(q)
      tracks <- bindSeq[TrackName](TrackName.Key, q)
      canonicals <- bindSeq[TrackCanonical](TrackCanonical.Key, q)
      route <- bindRouteRequest(q)
      sample <- Limits.readInt(SampleKey, q)
      newest <- bindNewest(q, default = true)
    yield BoatQuery(limits, timeRange, tracks, canonicals, route, sample, newest)

  def car(q: Query): Either[Errors, CarQuery] =
    for
      limits <- Limits(q)
      timeRange <- TimeRange(q)
    yield CarQuery(limits, timeRange)

  private def bindSeq[T: QueryParamDecoder](key: String, q: Query) =
    QueryParsers.list[T](key, q)

  private def bindNewest(q: Query, default: Boolean) =
    QueryParsers.parseOrDefault[Boolean](q, NewestKey, default)

  private def bindRouteRequest(q: Query): Either[Errors, Option[RouteRequest]] =
    val optEither = for
      lng1 <- readLongitude("lng1", q)
      lat1 <- readLatitude("lat1", q)
      lng2 <- readLongitude("lng2", q)
      lat2 <- readLatitude("lat2", q)
    yield for
      ln1 <- lng1
      la1 <- lat1
      ln2 <- lng2
      la2 <- lat2
    yield RouteRequest(Coord(ln1, la1), Coord(ln2, la2))
    optEither.map { e =>
      e.map(req => Option(req))
    }.getOrElse {
      Right(None)
    }

  private def readLongitude(key: String, q: Query) =
    transformDouble(key, q)(Longitude.build)

  private def readLatitude(key: String, q: Query) =
    transformDouble(key, q)(Latitude.build)

  private def transformDouble[T](key: String, q: Query)(
    transform: Double => Either[ErrorMessage, T]
  ) =
    readDouble(key, q).map { e =>
      e.flatMap { d =>
        transform(d).left.map { err =>
          Errors(SingleError.input(err.message))
        }
      }
    }

  private def readDouble(key: String, q: Query): Option[Either[Errors, Double]] =
    QueryParsers.parseOpt[Double](q, key)

trait LimitLike:
  def limit: Int
  def offset: Int
  def page = offset / limit + 1

case class Limits(limit: Int, offset: Int) extends LimitLike:
  def describe = s"limit $limit offset $offset"

object Limits:
  val Limit = "limit"
  val Offset = "offset"

  val DefaultLimit = 100000
  private val DefaultOffset = 0

  val default = Limits(DefaultLimit, DefaultOffset)

  def readInt(key: String, q: Query): Either[Errors, Option[Int]] =
    QueryParsers.parseOptE[Int](q, key)

  def apply(q: Query, defaultLimit: Int = DefaultLimit): Either[Errors, Limits] =
    for
      limit <- QueryParsers.parseOrDefault(q, Limit, defaultLimit)
      offset <- QueryParsers.parseOrDefault(q, Offset, DefaultOffset)
    yield Limits(limit, offset)

case class TimeRange(from: Option[Instant], to: Option[Instant]):
  def isEmpty = from.isEmpty && to.isEmpty
  def describe = (from, to) match
    case (Some(f), Some(t)) => s"$f - $t"
    case (None, Some(t))    => s"- $t"
    case (Some(f), None)    => s"$f -"
    case other              => ""

object TimeRange:
  private val From = "from"
  private val To = "to"

  val none = TimeRange(None, None)

  def recent(now: Instant): TimeRange =
    since(now.minus(5.minutes.toSeconds, ChronoUnit.SECONDS))

  def since(from: Instant): TimeRange =
    TimeRange(Option(from), None)

  def apply(q: Query): Either[Errors, TimeRange] =
    for
      from <- bindInstant(From, q)
      to <- bindInstant(To, q)
    yield TimeRange(from, to)

  private val instantDecoder =
    QueryParamDecoder.instantQueryParamDecoder(DateTimeFormatter.ISO_INSTANT)
  private val localDateEncoder =
    QueryParamDecoder.localDate(DateTimeFormatter.ISO_LOCAL_DATE)

  private def bindInstant(key: String, q: Query): Either[Errors, Option[Instant]] =
    QueryParsers
      .parseOptE[Instant](q, key)(instantDecoder)
      .orElse(
        bindLocalDate(key, q).map(_.map(_.atStartOfDay(TimeFormatter.helsinkiZone).toInstant))
      )

  private def bindLocalDate(key: String, q: Query): Either[Errors, Option[LocalDate]] =
    QueryParsers.parseOptE[LocalDate](q, key)(localDateEncoder)

  def parseInstant(in: String): Either[SingleError, Instant] =
    try Right(Instant.parse(in))
    catch
      case dte: DateTimeParseException =>
        Left(SingleError.input(s"Invalid instant: '$in'. ${dte.getMessage}"))

  def parseLocalDate(in: String): Either[SingleError, LocalDate] =
    try Right(LocalDate.parse(in))
    catch
      case dte: DateTimeParseException =>
        Left(SingleError.input(s"Invalid date: '$in'. ${dte.getMessage}"))
