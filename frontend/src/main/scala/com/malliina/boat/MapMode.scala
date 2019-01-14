package com.malliina.boat

sealed trait MapMode

object MapMode {

  // Centers on the latest coordinate
  case object Follow extends MapMode

  // Fits all coordinates on the screen
  case object Fit extends MapMode

  // Does nothing on coordinate updates
  case object Stay extends MapMode

}
