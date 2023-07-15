package com.malliina.boat

import com.malliina.datepicker.*

import scala.scalajs.js.Date

class DateHandler:
  def maxDate = new Date(Date.now())

  private var selectedFrom: Option[Date] = None
  private var selectedTo: Option[Date] = None

  def subscribeDate(picker: TempusDominus, other: TempusDominus, isFrom: Boolean) =
    picker.subscribe(
      "change.td",
      e =>
        val ce = e.asInstanceOf[ChangeEvent]
        val newDate = ce.date.toOption
        if isFrom then selectedFrom = newDate else selectedTo = newDate
        newDate.foreach { date =>
          other.updateOptions(
            TimeOptions(
              if isFrom then TimeRestrictions(min = newDate, max = Option(maxDate))
              else TimeRestrictions(min = None, max = newDate),
              TimeLocalization(DateFormats.default)
            ),
            reset = false
          )
        }
        updateSearch()
    )

  def updateSearch(): Unit =
    println(s"Updating search todo...")
