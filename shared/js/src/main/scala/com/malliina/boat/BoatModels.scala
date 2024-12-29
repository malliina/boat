package com.malliina.boat

import com.malliina.boat.html.BoatConversions
import org.scalajs.dom
import scalatags.JsDom.all.{Attr, AttrValue}

object BoatModels extends BoatModels

class BoatModels extends BoatConversions(scalatags.JsDom):
  override def makeStringAttr[T](write: T => String): AttrValue[T] =
    (t: dom.Element, a: Attr, v: T) => t.setAttribute(a.name, write(v))
