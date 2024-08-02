package com.malliina.boat.http

import io.circe.Codec

trait LimitLike:
  def limit: Int
  def offset: Int
  def page = offset / limit + 1

case class Limits(limit: Int, offset: Int) extends LimitLike derives Codec.AsObject:
  def describe = s"limit $limit offset $offset"
  def next = Limits(limit, offset + limit)
  def prev = Option.when(offset - limit >= 0)(Limits(limit, offset - limit))

object Limits:
  val Limit = "limit"
  val Offset = "offset"
