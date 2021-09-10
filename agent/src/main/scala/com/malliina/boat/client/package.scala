package com.malliina.boat

import scala.concurrent.duration.DurationInt
import scala.language.implicitConversions

package object client:
  implicit def durInt(i: Int): DurationInt = DurationInt(i)
