package com.malliina.boat.html

import com.malliina.boat.BoatFormats.{formatDistance, formatSpeed, formatTemp}
import com.malliina.boat.{DateVal, WrappedInt}
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.WrappedString
import play.api.mvc.Call
import scalatags.Text.all.{Attr, AttrValue, Frag, StringFrag, genericAttr, intFrag, stringFrag}
import scalatags.text.Builder

import scala.language.implicitConversions

object BoatImplicits extends BoatImplicits

trait BoatImplicits {
  implicit val callAttr = genericAttr[Call]

  implicit def speedHtml(s: SpeedM): StringFrag = stringFrag(formatSpeed(s))
  implicit def distanceHtml(d: DistanceM): StringFrag = stringFrag(formatDistance(d))
  implicit def tempHtml(t: Temperature): StringFrag = stringFrag(formatTemp(t))
  implicit def wrappedHtml[T <: WrappedString](w: T): StringFrag = stringFrag(w.value)
  implicit def dateHtml[T <: WrappedInt](t: T): Frag = intFrag(t.value)
  implicit def dateValHtml(d: DateVal): Frag = stringFrag(d.iso8601)
  implicit def wrappedIntAttr[T <: WrappedInt]: AttrValue[T] = boatStringAttr(i => s"${i.value}")

  def boatStringAttr[T](stringify: T => String): AttrValue[T] = { (t: Builder, a: Attr, v: T) =>
    t.setAttr(a.name, Builder.GenericAttrValueSource(stringify(v)))
  }
}
