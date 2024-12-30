package com.malliina.boat.html

import cats.Show
import com.malliina.boat.{DayVal, FormattedDate, FormattedDateTime, FormattedTime, Mmsi, MonthVal, VesselName, YearVal}
import scalatags.generic.Bundle

abstract class BoatConversions[Builder, Output <: FragT, FragT](
  val impl: Bundle[Builder, Output, FragT]
):
  import impl.all.{intFrag, stringFrag}
  import impl.Frag
  import impl.AttrValue
  given Conversion[DayVal, Frag] = (d: DayVal) => intFrag(d.day)
  given Conversion[MonthVal, Frag] = (d: MonthVal) => intFrag(d.month)
  given Conversion[YearVal, Frag] = (d: YearVal) => intFrag(d.year)
  given Conversion[FormattedTime, Frag] = (ft: FormattedTime) => stringFrag(ft.time)
  given Conversion[FormattedDate, Frag] = (fd: FormattedDate) => stringFrag(fd.date)
  given Conversion[FormattedDateTime, Frag] = (fdt: FormattedDateTime) => stringFrag(fdt.dateTime)
  given vesselFrag: Conversion[VesselName, Frag] = (vn: VesselName) => stringFrag(vn.name)
  given mmsiFrag: Conversion[Mmsi, Frag] = showFrag[Mmsi]

  def showFrag[T: Show]: Conversion[T, Frag] = (t: T) => Show[T].show(t)
  def showAttr[T: Show]: AttrValue[T] = makeStringAttr[T](t => Show[T].show(t))

  def makeStringAttr[T](write: T => String): AttrValue[T]
