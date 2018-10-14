package com.malliina.boat

import com.malliina.measure.{Distance, Speed, Temperature}

import scala.concurrent.duration.Duration

object BoatFormats {
  def formatSpeed(s: Speed) = "%.3f".format(s.toKnotsDouble)

  def formatDistance(d: Distance) = "%.3f".format(d.toKilometersDouble)

  def formatTemp(t: Temperature) = "%.1f".format(t.toCelsius)

  def formatDuration(d: Duration): String = {
    val seconds = d.toSeconds
    val s = seconds % 60
    val m = (seconds / 60) % 60
    val h = (seconds / (60 * 60)) % 24
    if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
  }
}
