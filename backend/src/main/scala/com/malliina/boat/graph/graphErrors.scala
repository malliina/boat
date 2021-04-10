package com.malliina.boat.graph

import com.malliina.boat.Coord

sealed trait GraphError

case class UnresolvedFrom(from: Coord) extends GraphError
case class UnresolvedTo(to: Coord) extends GraphError
case class NoRoute(from: Coord, to: Coord) extends GraphError
case object EmptyGraph extends GraphError
