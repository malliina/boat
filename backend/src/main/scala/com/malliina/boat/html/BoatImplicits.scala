package com.malliina.boat.html

import cats.Show
import cats.syntax.show.toShow
import com.malliina.boat.BoatFormats.{formatKnots, formatTemp}
import com.malliina.boat.{DateVal, WrappedInt}
import com.malliina.measure.{SpeedM, Temperature}
import com.malliina.values.WrappedString
import scalatags.Text.all.{Attr, AttrValue, Frag, Modifier, intFrag, stringFrag}
import scalatags.text.Builder

import scala.language.implicitConversions

object BoatImplicits extends BoatImplicits

trait BoatImplicits:
  given Conversion[SpeedM, Frag] = (s: SpeedM) => stringFrag(formatKnots(s))
  // given Conversion[DistanceM, Frag] = (d: DistanceM) => stringFrag(formatDistance(d))
  given Conversion[Temperature, Frag] = (t: Temperature) => stringFrag(formatTemp(t))
  given Conversion[DateVal, Frag] = (d: DateVal) => stringFrag(d.iso8601)
  given stringConv[T <: WrappedString]: Conversion[T, Frag] = (w: T) => stringFrag(w.value)
  given intConv[T <: WrappedInt]: Conversion[T, Frag] = (t: T) => intFrag(t.value)
  given showAttr[T: Show]: AttrValue[T] = (t: Builder, a: Attr, v: T) =>
    t.setAttr(a.name, Builder.GenericAttrValueSource(v.show))
  given showFrag[T: Show]: Conversion[T, Modifier] = (t: T) => stringFrag(t.show)
  given wrappedIntAttr[T <: WrappedInt]: AttrValue[T] = boatStringAttr(i => s"${i.value}")
  given wrappedStringAttr[T <: WrappedString]: AttrValue[T] = boatStringAttr(_.value)
  def boatStringAttr[T](stringify: T => String): AttrValue[T] = (t: Builder, a: Attr, v: T) =>
    t.setAttr(a.name, Builder.GenericAttrValueSource(stringify(v)))
