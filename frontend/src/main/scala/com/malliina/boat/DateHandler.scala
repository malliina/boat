package com.malliina.boat

import com.malliina.datepicker.*

import scala.scalajs.js.Date

object DateHandler:
  def timeOptions(restrictions: TimeRestrictions, locale: TimeLocale) =
    TimeOptions(
      restrictions,
      TimeLocalization(DateFormats.default, locale),
      IconOptions(
        "icons",
        "time-picker close",
        "time-picker clear",
        "time-picker time",
        "time-picker date",
        "time-picker up",
        "time-picker down",
        "time-picker previous",
        "time-picker next",
        "time-picker today"
      )
    )

class DateHandler:
  private var selectedFrom: Option[Date] = None
  private var selectedTo: Option[Date] = None

  def from = selectedFrom
  def to = selectedTo

  def onShortcut(s: Shortcut): Unit =
    val oneHour = 60 * 60 * 1000
    val age = s match
      case Shortcut.Last30min => oneHour / 2
      case Shortcut.Last2h    => oneHour * 2
      case Shortcut.Last12h   => oneHour * 12
      case Shortcut.Last24h   => oneHour * 24
      case Shortcut.Last48h   => oneHour * 48
    selectedFrom = Option(new Date(Date.now() - age))
    selectedTo = Option(new Date(Date.now()))

  def subscribeDate(
    picker: TempusDominus,
    other: TempusDominus,
    isFrom: Boolean,
    locale: TimeLocale
  )(
    onUpdate: Option[Date] => Unit
  ) =
    picker.subscribe(
      "hide.td",
      e =>
        val he = e.asInstanceOf[HideEvent]
        val newDate = he.date.toOption.filter(_ != null)
//        log.info(s"New date $newDate")
        if isFrom then selectedFrom = newDate else selectedTo = newDate
        newDate.foreach: _ =>
          other.updateOptions(
            DateHandler.timeOptions(
              if isFrom then TimeRestrictions(min = newDate, max = None)
              else TimeRestrictions(min = None, max = newDate),
              locale
            ),
            reset = false
          )
        // User might clear input manually, in which case there's no value but a need to refresh
        onUpdate(if isFrom then selectedFrom else selectedTo)
    )
