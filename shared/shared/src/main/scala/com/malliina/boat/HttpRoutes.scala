package com.malliina.boat

object HttpRoutes:
  def full(name: TrackName) = s"/tracks/$name/full"
  def chart(name: TrackName) = s"/tracks/$name/chart"
