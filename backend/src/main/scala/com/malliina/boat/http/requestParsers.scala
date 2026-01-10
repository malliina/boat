package com.malliina.boat.http

import cats.implicits.*
import com.malliina.http.{Errors, SingleError}
import com.malliina.http4s.QueryParsers
import com.malliina.boat.{BoatName, CarUpdateId, Constants, Coord, FromTo, FrontKeys, Latitude, Longitude, Mmsi, RouteRequest, SearchQuery, TimeFormatter, Timings, TrackCanonical, TrackName, VesselName}
import com.malliina.measure.{DistanceIntM, DistanceM}
import com.malliina.values.{Email, ErrorMessage}
import io.circe.Codec
import org.http4s.{Headers, Query, QueryParamDecoder, Request}
import org.typelevel.ci.CIString

import java.time.{Instant, LocalDate}
import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.DurationInt

object QueryDecoders extends QueryDecoders

private trait QueryDecoders:
  given QueryParamDecoder[TrackName] =
    QueryParsers.decoder(TrackName.build)

  given QueryParamDecoder[TrackCanonical] =
    QueryParsers.decoder(TrackCanonical.build)

  given QueryParamDecoder[DistanceM] =
    QueryParamDecoder.doubleQueryParamDecoder.map(d => DistanceM(d))

  given QueryParamDecoder[Mmsi] =
    QueryParsers.decoder[Mmsi](Mmsi.parse)

  given QueryParamDecoder[VesselName] =
    QueryParsers.decoder[VesselName](VesselName.build)

import QueryDecoders.given

case class BoatEmailRequest[T](user: Email, query: T, headers: Headers)
  extends BoatRequest[T, Email]

case class AnyBoatRequest[T, U](user: U, query: T, headers: Headers) extends BoatRequest[T, U]

trait BoatRequest[T, U]:
  def user: U
  def query: T
  def headers: Headers

case class UserRequest[F[_], U](user: U, req: Request[F]):
  def headers = req.headers

case class JsonRequest[F[_], T, U](user: U, payload: T, req: Request[F], receivedAt: Instant)

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
    defaultLimit: Int = LimitsBuilder.DefaultLimit
  ): Either[Errors, TrackQuery] =
    for
      sort <- TrackSort(q)
      order <- SortOrder(q)
      limits <- LimitsBuilder(q, defaultLimit)
    yield TrackQuery(sort, order, limits)

case class TracksQuery(sources: Seq[BoatName], query: TrackQuery):
  def limits = query.limits
  def next = TracksQuery(sources, query.copy(limits = limits.next))
  def prev = limits.prev.map(p => TracksQuery(sources, query.copy(limits = p)))

object TracksQuery:
  val BoatsKey = "boats"

  given QueryParamDecoder[BoatName] = QueryParsers.decoder(s => BoatName.build(CIString(s)))

  def apply(q: Query): Either[Errors, TracksQuery] = for
    boats <- QueryParsers.list[BoatName](BoatsKey, q)
    query <- TrackQuery(q)
  yield TracksQuery(boats, query)

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
      .map: e =>
        e.flatMap: s =>
          all.find(_.name == s).toRight(Errors(SingleError.input(s"Unknown $key value: '$s'.")))

case class VesselsQuery(term: Option[String], limits: Limits) derives Codec.AsObject

object VesselsQuery:
  def query(q: Query): Either[Errors, VesselsQuery] =
    for
      term <- QueryParsers.parseOptE[String](q, "term")
      limits <- LimitsBuilder(q)
    yield VesselsQuery(term.filter(_.nonEmpty), limits)

trait VesselFilters:
  def vessels: Seq[VesselName]
  def mmsis: Seq[Mmsi]
  def timeRange: TimeRange
  def limits: Limits

  def describe: String = format(TimeFormatter.default)
  def format(formatter: TimeFormatter): String

case class VesselQuery(
  vessels: Seq[VesselName],
  mmsis: Seq[Mmsi],
  timeRange: TimeRange,
  limits: Limits
) extends VesselFilters derives Codec.AsObject:
  private def describeNames = if vessels.isEmpty then "" else s"names ${vessels.mkString(", ")} "
  private def describeMmsis = if mmsis.isEmpty then "" else s"mmsis ${mmsis.mkString(", ")} "
  private def describeTime(formatter: TimeFormatter) =
    if timeRange.isEmpty then "" else s"time ${timeRange.format(formatter)} "
  private def describeLimits = s"limit ${limits.limit} offset ${limits.offset}"
  def format(formatter: TimeFormatter) =
    s"$describeNames$describeMmsis${describeTime(formatter)}$describeLimits"

object VesselQuery:
  given QueryParamDecoder[Mmsi] =
    QueryParsers.decoder[Mmsi](Mmsi.parse)
  given QueryParamDecoder[VesselName] =
    QueryParsers.decoder[VesselName](VesselName.build)

  def query(q: Query): Either[Errors, VesselQuery] =
    for
      name <- QueryParsers.list[VesselName](VesselName.Key, q)
      mmsi <- QueryParsers.list[Mmsi](Mmsi.Key, q)
      limits <- LimitsBuilder(q)
      time <- TimeRange(q)
    yield VesselQuery(name, mmsi, time, limits)

case class CarQuery(limits: Limits, timeRange: TimeRange, ids: List[CarUpdateId]):
  private def timeDescribe = timeRange.describe
  private def space = if timeDescribe.isEmpty then "" else " "
  def describe = s"${timeRange.describe}${space}with ${limits.describe}"

object CarQuery:
  def ids(list: List[CarUpdateId]) = CarQuery(LimitsBuilder.default, TimeRange.none, list)

case class Near(coord: Coord, radius: DistanceM, limits: Limits):
  def limit = limits.limit

object Near:
  val Key = "near"
  val Radius = "radius"

  def apply(q: Query): Either[Errors, Near] =
    for
      from <- BoatQuery.bindCoord(FrontKeys.Lng, FrontKeys.Lat, q)
      radius <- QueryParsers.parseOrDefault[DistanceM](q, Near.Radius, 1.kilometers)
      limits <- LimitsBuilder(q, defaultLimit = 20)
    yield Near(from, radius, limits)

  def opt(q: Query): Either[Errors, Option[Near]] =
    for
      from <- BoatQuery.readCoordOpt(FrontKeys.Lng, FrontKeys.Lat, q)
      radius <- QueryParsers.parseOrDefault[DistanceM](q, Near.Radius, 1.kilometers)
      limits <- LimitsBuilder(q, defaultLimit = 20)
    yield from.map(c => Near(c, radius, limits))

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
  newest: Boolean,
  tracksLimit: Option[Int],
  near: Option[Near],
  vessels: Seq[VesselName],
  mmsis: Seq[Mmsi]
) extends VesselFilters:
  def hasVesselFilters = vessels.nonEmpty || mmsis.nonEmpty
  def neTracks = tracks.toList.toNel
  def neCanonicals = canonicals.toList.toNel
  def limit = limits.limit
  def offset = limits.offset
  def from = timeRange.from
  def to = timeRange.to
  def simple =
    val fromTo = FromTo(
      from = timeRange.from.map(BoatQuery.instantFormatter.format),
      to = timeRange.to.map(BoatQuery.instantFormatter.format)
    )
    SearchQuery(limits, fromTo, tracks, canonicals, route, sample, newest)
  def format(formatter: TimeFormatter): String =
    val timeFrom = from.map(f => s"from ${formatter.formatDateTime(f)}").getOrElse("")
    val timeTo = to.map(t => s"to ${formatter.formatDateTime(t)}").getOrElse("")
    val cs =
      if canonicals.nonEmpty then s"canonicals ${canonicals.map(c => s"'$c'").mkString(", ")}"
      else ""
    val ts = if tracks.nonEmpty then s"tracks ${tracks.map(t => s"'$t'").mkString(",")}" else ""
    val n = near.map(n => s"within ${n.radius} of ${n.coord}").getOrElse("")
    val describeLimit = s"limit ${limits.limit} offset ${limits.offset}"
    val describeNewest = s"newest $newest"
    val describeVessels = if vessels.isEmpty then "" else s"names ${vessels.mkString(", ")}"
    val describeMmsis = if mmsis.isEmpty then "" else s"mmsis ${mmsis.mkString(", ")}"
    Seq(
      timeFrom,
      timeTo,
      cs,
      ts,
      n,
      describeLimit,
      describeNewest,
      describeVessels,
      describeMmsis
    ).filter(_.nonEmpty).mkString(" ")

object BoatQuery:
  private val instantFormatter = DateTimeFormatter.ISO_INSTANT

  private val NewestKey = FrontKeys.Newest
  private val SampleKey = FrontKeys.SampleKey
  private val TracksLimitKey = FrontKeys.TracksLimit
  private val DefaultSample = Constants.DefaultSample

  val empty = BoatQuery(
    Limits(0, 0),
    TimeRange(None, None),
    Nil,
    Nil,
    None,
    None,
    newest = true,
    tracksLimit = None,
    near = None,
    vessels = Nil,
    mmsis = Nil
  )

  def tracks(tracks: Seq[TrackName]): BoatQuery =
    BoatQuery(
      LimitsBuilder.default,
      TimeRange(None, None),
      tracks,
      Nil,
      None,
      Option(DefaultSample),
      newest = false,
      tracksLimit = None,
      near = None,
      vessels = Nil,
      mmsis = Nil
    )

  def apply(q: Query): Either[Errors, BoatQuery] =
    for
      limits <- LimitsBuilder(q, defaultLimit = 100000)
      timeRange <- TimeRange(q)
      tracks <- bindSeq[TrackName](TrackName.Key, q)
      canonicals <- bindSeq[TrackCanonical](TrackCanonical.Key, q)
      route <- bindRouteRequest(q)
      sample <- LimitsBuilder.readInt(SampleKey, q)
      near <- Near.opt(q)
      newest <- bindNewest(q, default = timeRange.isEmpty && near.isEmpty)
      tracksLimit <- QueryParsers.parseOptE[Int](q, TracksLimitKey)
      vessels <- bindSeq[VesselName](VesselName.Key, q)
      mmsis <- bindSeq[Mmsi](Mmsi.Key, q)
    yield BoatQuery(
      limits,
      timeRange,
      tracks,
      canonicals,
      route,
      sample,
      newest,
      tracksLimit,
      near,
      vessels,
      mmsis
    )

  def car(q: Query): Either[Errors, CarQuery] =
    for
      limits <- LimitsBuilder(q)
      timeRange <- TimeRange(q)
    yield CarQuery(limits, timeRange, Nil)

  private def bindSeq[T: QueryParamDecoder](key: String, q: Query) =
    QueryParsers.list[T](key, q)

  private def bindNewest(q: Query, default: Boolean) =
    QueryParsers.parseOrDefault[Boolean](q, NewestKey, default)

  private def bindRouteRequest(q: Query): Either[Errors, Option[RouteRequest]] =
    for
      coord1 <- readCoordOpt("lng1", "lat1", q)
      coord2 <- readCoordOpt("lng2", "lat2", q)
    yield coord1.flatMap(c1 => coord2.map(c2 => RouteRequest(c1, c2)))

  def bindCoord(
    lngKey: String,
    latKey: String,
    q: Query
  ): Either[Errors, Coord] =
    readCoordOpt(lngKey, latKey, q).flatMap: opt =>
      opt.toRight(Errors(s"Specify $lngKey and $latKey."))

  def readCoordOpt(
    lngKey: String,
    latKey: String,
    q: Query
  ): Either[Errors, Option[Coord]] =
    for
      lngResult <- transformDouble(lngKey, q)(Longitude.build)
      latResult <- transformDouble(latKey, q)(Latitude.build)
    yield for
      lng <- lngResult
      lat <- latResult
    yield Coord(lng, lat)

  private def transformDouble[T](key: String, q: Query)(
    transform: Double => Either[ErrorMessage, T]
  ): Either[Errors, Option[T]] =
    readDouble(key, q).flatMap: opt =>
      opt
        .map: d =>
          transform(d).left.map(err => Errors(SingleError.input(err.message))).map(t => Option(t))
        .getOrElse:
          Right(None)

  private def readDouble(key: String, q: Query): Either[Errors, Option[Double]] =
    QueryParsers.parseOptE[Double](q, key)

object LimitsBuilder:
  val DefaultLimit = 50
  private val DefaultOffset = 0

  val default = Limits(DefaultLimit, DefaultOffset)

  def readInt(key: String, q: Query): Either[Errors, Option[Int]] =
    QueryParsers.parseOptE[Int](q, key)

  def apply(q: Query, defaultLimit: Int = DefaultLimit): Either[Errors, Limits] =
    for
      limit <- QueryParsers.parseOrDefault(q, Limits.Limit, defaultLimit)
      offset <- QueryParsers.parseOrDefault(q, Limits.Offset, DefaultOffset)
    yield Limits(limit, offset)

case class TimeRange(from: Option[Instant], to: Option[Instant]) derives Codec.AsObject:
  def isEmpty = from.isEmpty && to.isEmpty
  def describe = format(TimeFormatter.fi)
  def format(formatter: TimeFormatter) = (from, to) match
    case (Some(f), Some(t)) => s"${formatter.formatDateTime(f)} - ${formatter.formatDateTime(t)}"
    case (None, Some(t))    => s"- ${formatter.formatDateTime(t)}"
    case (Some(f), None)    => s"${formatter.formatDateTime(f)} -"
    case _                  => ""

object TimeRange:
  private val From = Timings.From
  private val To = Timings.To

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
      .parseOptE[Instant](q, key)(using instantDecoder)
      .orElse(
        bindLocalDate(key, q).map(_.map(_.atStartOfDay(TimeFormatter.helsinkiZone).toInstant))
      )

  private def bindLocalDate(key: String, q: Query): Either[Errors, Option[LocalDate]] =
    QueryParsers.parseOptE[LocalDate](q, key)(using localDateEncoder)

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
