package com.malliina.boat

import com.malliina.measure.DistanceM

object Calc:
  def kWhPer100km(energy: Energy, distance: DistanceM): Double =
    energy.wattHours / distance.toMeters * 100
