package com.malliina

import scala.scalajs.js

// Yeah I don't know any better
extension [T <: js.Any](o: Option[T])
  def any: js.Any = o.map(t => t: js.Any).getOrElse(js.undefined)
extension (o: Option[String]) def anys: js.Any = o.map(t => t: js.Any).getOrElse(js.undefined)
extension (o: Option[Double]) def anyd: js.Any = o.map(t => t: js.Any).getOrElse(js.undefined)
