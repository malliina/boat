package com.malliina.boat.html

import com.malliina.boat.{DayVal, FormattedDate, FormattedDateTime, FormattedTime, MonthVal, VesselName, YearVal}
import scalatags.generic.Bundle

class BoatConversions[Builder, Output <: FragT, FragT](val impl: Bundle[Builder, Output, FragT]):
  import impl.all.{intFrag, stringFrag}
  import impl.Frag
  given Conversion[DayVal, Frag] = (d: DayVal) => intFrag(d.day)
  given Conversion[MonthVal, Frag] = (d: MonthVal) => intFrag(d.month)
  given Conversion[YearVal, Frag] = (d: YearVal) => intFrag(d.year)
  given Conversion[FormattedTime, Frag] = (ft: FormattedTime) => stringFrag(ft.time)
  given Conversion[FormattedDate, Frag] = (fd: FormattedDate) => stringFrag(fd.date)
  given Conversion[FormattedDateTime, Frag] = (fdt: FormattedDateTime) => stringFrag(fdt.dateTime)
  given Conversion[VesselName, Frag] = (vn: VesselName) => stringFrag(vn.name)
//  given [T: Show]: Conversion[T, Frag] = (t: T) => Show[T].show(t)
