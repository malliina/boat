package com.malliina.boat

package object graph:
  implicit class CoordOps(lng: Double):
    def lngLat(lat: Double) = Coord.buildOrFail(lng, lat)
