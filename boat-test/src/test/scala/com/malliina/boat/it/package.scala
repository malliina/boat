package com.malliina.boat

import scala.concurrent.duration.DurationInt
import scala.language.implicitConversions

package object it {
  implicit def durInt(i: Int): DurationInt = new DurationInt(i)
}
