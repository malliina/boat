package com.malliina.boat.html

import cats.Show
import cats.syntax.show.toShow
import com.malliina.boat.BoatFormats.{formatKnots, formatTemp}
import com.malliina.boat.{DateVal, SourceType, WrappedInt}
import com.malliina.measure.{SpeedM, Temperature}
import com.malliina.values.WrappedString
import com.malliina.http.CSRFToken
import org.http4s.Uri
import scalatags.Text.all.{Attr, AttrValue, Frag, Modifier, intFrag, stringFrag}
import scalatags.text.Builder

import scala.language.implicitConversions

object BoatImplicits extends BoatImplicits

trait BoatImplicits:
  given AttrValue[Uri] = boatStringAttr(_.renderString)
  given AttrValue[CSRFToken] = boatStringAttr(_.value)
  given Conversion[SpeedM, Frag] = (s: SpeedM) => stringFrag(formatKnots(s))
  given Conversion[Temperature, Frag] = (t: Temperature) => stringFrag(formatTemp(t))
  given Conversion[DateVal, Frag] = (d: DateVal) => stringFrag(d.iso8601)
  given stringConv[T <: WrappedString]: Conversion[T, Frag] = (w: T) => stringFrag(w.value)
  given intConv[T <: WrappedInt]: Conversion[T, Frag] = (t: T) => intFrag(t.value)
  given [T: Show]: Conversion[T, Modifier] = (t: T) => stringFrag(t.show)
  given wrappedIntAttr[T <: WrappedInt]: AttrValue[T] = boatStringAttr(i => s"${i.value}")
  given wrappedStringAttr[T <: WrappedString]: AttrValue[T] = boatStringAttr(_.value)
  given AttrValue[SourceType] = boatStringAttr(_.name)
  given [T: Show]: AttrValue[T] = boatStringAttr(t => t.show)
  private def boatStringAttr[T](stringify: T => String): AttrValue[T] =
    (t: Builder, a: Attr, v: T) => t.setAttr(a.name, Builder.GenericAttrValueSource(stringify(v)))
