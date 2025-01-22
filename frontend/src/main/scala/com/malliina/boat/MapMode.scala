package com.malliina.boat

enum MapMode:
  // Centers on the latest coordinate
  case Follow
  // Fits all coordinates on the screen
  case Fit
  // Does nothing on coordinate updates
  case Stay
