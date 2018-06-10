package com.malliina.boat.http

import com.malliina.values.ErrorMessage
import play.api.mvc.{QueryStringBindable, RequestHeader}

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
