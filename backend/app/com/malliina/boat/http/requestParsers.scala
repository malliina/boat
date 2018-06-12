package com.malliina.boat.http

import java.time.Instant
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

import com.malliina.values.ErrorMessage
import play.api.mvc.{QueryStringBindable, RequestHeader}

import scala.concurrent.duration.DurationInt

case class BoatQuery(limits: Limits, timeRange: TimeRange) {
  def limit = limits.limit

  def offset = limits.offset

  def from = timeRange.from

  def to = timeRange.to
}

object BoatQuery {
  def recent(now: Instant): BoatQuery =
    BoatQuery(Limits.default, TimeRange.recent(now))

  def apply(rh: RequestHeader): Either[ErrorMessage, BoatQuery] =
    for {
      limits <- Limits(rh)
      timeRange <- TimeRange(rh)
    } yield BoatQuery(limits, timeRange)
}

case class Limits(limit: Int, offset: Int)

object Limits {
  val Limit = "limit"
  val Offset = "offset"

  val DefaultLimit = 10000
  val DefaultOffset = 0

  val default = Limits(1000, 0)

  def readIntOrElse(rh: RequestHeader, key: String, default: Int): Either[ErrorMessage, Int] =
    QueryStringBindable.bindableInt.bind(key, rh.queryString).getOrElse(Right(default))
      .left.map(ErrorMessage.apply)

  def apply(rh: RequestHeader): Either[ErrorMessage, Limits] = {
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

  def apply(rh: RequestHeader): Either[ErrorMessage, TimeRange] =
    for {
      from <- bindInstant(From, rh)
      to <- bindInstant(To, rh)
    } yield TimeRange(from, to)

  def bindInstant(key: String, rh: RequestHeader): Either[ErrorMessage, Option[Instant]] =
    QueryStringBindable.bindableString.bind(key, rh.queryString)
      .map(e => e.flatMap(parseInstant).map(Option.apply)).getOrElse(Right(None))

  def parseInstant(in: String): Either[ErrorMessage, Instant] =
    try {
      Right(Instant.parse(in))
    } catch {
      case dte: DateTimeParseException =>
        Left(ErrorMessage(s"Invalid instant: '$in'. ${dte.getMessage}"))
    }
}
