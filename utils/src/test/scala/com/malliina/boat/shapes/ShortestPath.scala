package com.malliina.boat.shapes

import com.malliina.boat.Coord

trait Distance

class ShortestPath {
  def shortest(from: Coord, to: Coord, within: Seq[Seq[Coord]]): Seq[Coord] = ???
  def cost(from: Coord, to: Coord): Distance = ???
}
