package com.malliina.boat.html

import cats.Show
import cats.syntax.show.toShow
import com.malliina.boat.BoatFormats.{formatKnots, formatTemp}
import com.malliina.boat.http4s.Reverse
import com.malliina.boat.{BoatModels, DateVal, DayVal, MonthVal, SourceType, YearVal}
import com.malliina.measure.{SpeedM, Temperature}
import com.malliina.values.{ValidatingCompanion, WrappedString}
import com.malliina.http.CSRFToken
import org.http4s.Uri
import scalatags.Text.all.{Attr, AttrValue, Frag, Modifier, modifier, stringFrag, tag}
import scalatags.text.Builder

import scala.language.implicitConversions

object BoatImplicits extends BoatImplicits

class BoatSyntax extends BoatModels with BoatImplicits:
  val reverse = Reverse
  val empty = modifier()
  val nav = tag("nav")

  def classes(cs: String*) = cs.filter(_.nonEmpty).mkString(" ")

trait BoatImplicits:
  given AttrValue[Uri] = boatStringAttr(_.renderString)
  given AttrValue[CSRFToken] = boatStringAttr(_.value)
  given AttrValue[DayVal] = intAttr(DayVal)
  given AttrValue[MonthVal] = intAttr(MonthVal)
  given AttrValue[YearVal] = intAttr(YearVal)

  given Conversion[SpeedM, Frag] = (s: SpeedM) => stringFrag(formatKnots(s))
  given Conversion[Temperature, Frag] = (t: Temperature) => stringFrag(formatTemp(t))
  given Conversion[DateVal, Frag] = (d: DateVal) => stringFrag(d.iso8601)
  given stringConv[T <: WrappedString]: Conversion[T, Frag] = (w: T) => stringFrag(w.value)
  given [T: Show]: Conversion[T, Modifier] = (t: T) => stringFrag(t.show)
  given wrappedStringAttr[T <: WrappedString]: AttrValue[T] = boatStringAttr(_.value)
  given AttrValue[SourceType] = boatStringAttr(_.name)
  given [T: Show]: AttrValue[T] = boatStringAttr(t => t.show)

  private def intAttr[T, C <: ValidatingCompanion[Int, T]](c: C) =
    boatStringAttr[T](v => s"${c.write(v)}")
  private def boatStringAttr[T](stringify: T => String): AttrValue[T] =
    (t: Builder, a: Attr, v: T) => t.setAttr(a.name, Builder.GenericAttrValueSource(stringify(v)))
