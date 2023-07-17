package com.malliina.boat

import com.malliina.datepicker.*

import scala.scalajs.js.Date

class DateHandler(log: BaseLogger):
  def maxDate = new Date(Date.now())

  private var selectedFrom: Option[Date] = None
  private var selectedTo: Option[Date] = None

  def from = selectedFrom
  def to = selectedTo

  def subscribeDate(picker: TempusDominus, other: TempusDominus, isFrom: Boolean)(
    onUpdate: Option[Date] => Unit
  ) =
    picker.subscribe(
      "change.td",
      e =>
        val ce = e.asInstanceOf[ChangeEvent]
        val newDate = ce.date.toOption
        log.info(s"New date $newDate valid ${ce.isValid} clear ${ce.isClear}")
        if isFrom then selectedFrom = newDate else selectedTo = newDate
        newDate.foreach { date =>
          other.updateOptions(
            TimeOptions(
              if isFrom then TimeRestrictions(min = newDate, max = None)
              else TimeRestrictions(min = None, max = newDate),
              TimeLocalization(DateFormats.default)
            ),
            reset = false
          )
        }
        // User might clear input manually, in which case there's no value but a need to refresh
        onUpdate(if isFrom then selectedFrom else selectedTo)
    )
