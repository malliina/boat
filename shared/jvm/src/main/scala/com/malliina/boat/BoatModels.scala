package com.malliina.boat

import com.malliina.boat.html.BoatConversions
import scalatags.Text.all.{Attr, AttrValue}
import scalatags.text.Builder

object BoatModels extends BoatModels

class BoatModels extends BoatConversions(scalatags.Text):
  override def makeStringAttr[T](write: T => String): AttrValue[T] =
    (t: Builder, a: Attr, v: T) => t.setAttr(a.name, Builder.GenericAttrValueSource(write(v)))
