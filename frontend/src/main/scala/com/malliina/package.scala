package com

import scala.scalajs.js

package object malliina {
  // Yeah I don't know any better
  implicit class OptionOps[T <: js.Any](val o: Option[T]) extends AnyVal {
    def any: js.Any = o.map(t => t: js.Any).getOrElse(js.undefined)
  }
  implicit class OptionStringOps(val o: Option[String]) extends AnyVal {
    def any: js.Any = o.map(t => t: js.Any).getOrElse(js.undefined)
  }
  implicit class OptionDoubleOps(val o: Option[Double]) extends AnyVal {
    def any: js.Any = o.map(t => t: js.Any).getOrElse(js.undefined)
  }
}
