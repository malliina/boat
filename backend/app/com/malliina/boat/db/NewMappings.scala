package com.malliina.boat.db

import java.time.Instant
import java.util.Date

import com.malliina.values.UserId
import io.getquill.MappedEncoding

object NewMappings extends NewMappings

trait NewMappings {
  implicit val instantDecoder = MappedEncoding[Date, Instant](d => d.toInstant)
  implicit val instantEncoder = MappedEncoding[Instant, Date](i => Date.from(i))

  implicit val userIdDecoder = MappedEncoding[Long, UserId](UserId.apply)
  implicit val userIdEncoder = MappedEncoding[UserId, Long](_.id)
}
