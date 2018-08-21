package com.malliina.boat.http

import java.time.Instant
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

import com.malliina.boat.{SingleError, TrackName}
import com.malliina.values.Email
import play.api.mvc.{QueryStringBindable, Request, RequestHeader}

import scala.concurrent.duration.DurationInt

case class BoatEmailRequest[T](query: T, email: Email, rh: RequestHeader)

case class BoatRequest[U, B](user: U, req: Request[B]) {
  def body: B = req.body

  def headers = req.headers
}

sealed abstract class TrackSort(val name: String) extends Named

object TrackSort extends EnumLike[TrackSort] {
  val key = "sort"
  val default = Recent
  val all: Seq[TrackSort] = Seq(Recent, Points)

  case object Recent extends TrackSort("recent")

  case object Points extends TrackSort("points")

}

sealed abstract class SortOrder(val name: String) extends Named

object SortOrder extends EnumLike[SortOrder] {
  val key = "order"
  val default = Desc
  val all: Seq[SortOrder] = Seq(Asc, Desc)

  case object Asc extends SortOrder("asc")

  case object Desc extends SortOrder("desc")

}

case class TrackQuery(sort: TrackSort, order: SortOrder, limits: Limits)

object TrackQuery {
  def apply(rh: RequestHeader): Either[SingleError, TrackQuery] =
    for {
      sort <- TrackSort(rh)
      order <- SortOrder(rh)
      limits <- Limits(rh)
    } yield TrackQuery(sort, order, limits)
}

trait Named {
  def name: String

  override def toString = name
}

abstract class EnumLike[T <: Named] {
  def key: String

  def default: T

  def all: Seq[T]

  def apply(rh: RequestHeader): Either[SingleError, T] = apply(key, rh, default)

  def apply(key: String, rh: RequestHeader, default: T): Either[SingleError, T] =
    apply(key, rh).getOrElse(Right(default))

  def apply(key: String, rh: RequestHeader): Option[Either[SingleError, T]] =
    QueryStringBindable.bindableString.bind(key, rh.queryString)
      .map(e => e.flatMap(s => all.find(_.name == s).toRight(SingleError(s"Unknown $key value: '$s'."))))
}

/**
  * @param tracks tracks to return
  * @param newest true to return the newest track if no tracks are specified, false means all tracks are returned
  */
case class BoatQuery(limits: Limits, timeRange: TimeRange, tracks: Seq[TrackName], newest: Boolean) {
  def limit = limits.limit

  def offset = limits.offset

  def from = timeRange.from

  def to = timeRange.to
}

object BoatQuery {
  val NewestKey = "newest"
  val TrackKey = "track"
  val bindTrack: QueryStringBindable[TrackName] =
    QueryStringBindable.bindableString.transform[TrackName](TrackName.apply, _.name)
  val tracksBindable = QueryStringBindable.bindableSeq[TrackName](bindTrack)

  def tracks(tracks: Seq[TrackName]) = BoatQuery(Limits.default, TimeRange(None, None), tracks, newest = false)

  def recent(now: Instant): BoatQuery =
    BoatQuery(Limits.default, TimeRange.recent(now), Nil, newest = false)

  def apply(rh: RequestHeader): Either[SingleError, BoatQuery] =
    for {
      limits <- Limits(rh)
      timeRange <- TimeRange(rh)
      tracks <- bindTracks(rh)
      newest <- bindBool(rh, default = true)
    } yield BoatQuery(limits, timeRange, tracks, newest)

  def bindTracks(rh: RequestHeader): Either[SingleError, Seq[TrackName]] =
    tracksBindable.bind(TrackKey, rh.queryString).map(_.left.map(SingleError.apply)).getOrElse(Right(Nil))

  def bindBool(rh: RequestHeader, default: Boolean) =
    QueryStringBindable.bindableBoolean.bind(NewestKey, rh.queryString).getOrElse(Right(default)).left.map(SingleError.apply)
}

case class Limits(limit: Int, offset: Int)

object Limits {
  val Limit = "limit"
  val Offset = "offset"

  val DefaultLimit = 30000
  val DefaultOffset = 0

  val default = Limits(DefaultLimit, DefaultOffset)

  def readIntOrElse(rh: RequestHeader, key: String, default: Int): Either[SingleError, Int] =
    QueryStringBindable.bindableInt.bind(key, rh.queryString).getOrElse(Right(default))
      .left.map(SingleError.apply)

  def apply(rh: RequestHeader): Either[SingleError, Limits] = {
    for {
      limit <- readIntOrElse(rh, Limit, DefaultLimit)
      offset <- readIntOrElse(rh, Offset, DefaultOffset)
    } yield Limits(limit, offset)
  }
}

case class TimeRange(from: Option[Instant], to: Option[Instant])

object TimeRange {
  val From = "from"
  val To = "to"

  def recent(now: Instant): TimeRange =
    since(Instant.now().minus(5.minutes.toSeconds, ChronoUnit.SECONDS))

  def since(from: Instant): TimeRange =
    TimeRange(Option(from), None)

  def apply(rh: RequestHeader): Either[SingleError, TimeRange] =
    for {
      from <- bindInstant(From, rh)
      to <- bindInstant(To, rh)
    } yield TimeRange(from, to)

  def bindInstant(key: String, rh: RequestHeader): Either[SingleError, Option[Instant]] =
    QueryStringBindable.bindableString.bind(key, rh.queryString)
      .map(e => e.flatMap(parseInstant).map(Option.apply)).getOrElse(Right(None))

  def parseInstant(in: String): Either[SingleError, Instant] =
    try {
      Right(Instant.parse(in))
    } catch {
      case dte: DateTimeParseException =>
        Left(SingleError(s"Invalid instant: '$in'. ${dte.getMessage}"))
    }
}
