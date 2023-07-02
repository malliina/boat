package com.malliina.boat.html

import com.malliina.boat.BoatFormats.{formatDistance, formatSpeed, formatTemp}
import com.malliina.boat.{DateVal, DeviceId, WrappedInt}
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.WrappedString
import scalatags.Text.all.{Attr, AttrValue, Frag, intFrag, stringFrag}
import scalatags.text.Builder

import scala.language.implicitConversions

object BoatImplicits extends BoatImplicits

trait BoatImplicits:
  implicit def speedHtml(s: SpeedM): Frag = stringFrag(formatSpeed(s))
  implicit def distanceHtml(d: DistanceM): Frag = stringFrag(formatDistance(d))
  implicit def tempHtml(t: Temperature): Frag = stringFrag(formatTemp(t))
  implicit def wrappedHtml[T <: WrappedString](w: T): Frag = stringFrag(w.value)
  implicit def dateHtml[T <: WrappedInt](t: T): Frag = intFrag(t.value)
  implicit def dateValHtml(d: DateVal): Frag = stringFrag(d.iso8601)
  implicit def wrappedIntAttr[T <: WrappedInt]: AttrValue[T] = boatStringAttr(i => s"${i.value}")

  implicit val deviceIdAttr: AttrValue[DeviceId] = (t: Builder, a: Attr, v: DeviceId) =>
    t.setAttr(a.name, Builder.GenericAttrValueSource(DeviceId.write(v).toString))

//  implicit def attrId[T <: WrappedId]: AttrValue[T] = (t: Builder, a: Attr, v: T) =>
//    t.setAttr(a.name, Builder.GenericAttrValueSource(s"$v"))

  def boatStringAttr[T](stringify: T => String): AttrValue[T] = (t: Builder, a: Attr, v: T) =>
    t.setAttr(a.name, Builder.GenericAttrValueSource(stringify(v)))
