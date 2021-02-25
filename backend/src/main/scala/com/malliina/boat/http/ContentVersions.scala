package com.malliina.boat.http

import org.http4s.MediaType

object ContentVersions extends ContentVersions

trait ContentVersions {
  val Version1 = version(1)
  // TrackSummary -> TrackRef
  val Version2 = version(2)

  private def version(number: Int) = MediaType.unsafeParse(s"application/vnd.boat.v$number+json")
}
