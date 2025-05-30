package com.malliina.datepicker

import org.scalajs.dom.Element

import scala.annotation.nowarn
import scala.scalajs.js
import scala.scalajs.js.Date
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.JSConverters.JSRichOption

@js.native
trait DateFormats extends js.Object:
  def LTS: String = js.native
  def LT: String = js.native
  def L: String = js.native
  def LL: String = js.native
  def LLL: String = js.native
  def LLLL: String = js.native

object DateFormats:
  val default = apply(
    "HH:mm:ss",
    "HH:mm",
    "dd-MM-yyyy",
    "MMMM d, yyyy",
    "MMMM d, yyyy HH:mm",
    "dddd, MMMM d, yyyy HH:mm"
  )
  def apply(
    lts: String,
    lt: String,
    l: String,
    ll: String,
    lll: String,
    llll: String
  ): DateFormats =
    literal(LTS = lts, LT = lt, L = l, LL = ll, LLL = lll, LLLL = llll).asInstanceOf[DateFormats]

@js.native
trait TimeLocalization extends js.Object:
  def dateFormats: DateFormats = js.native
  def hourCycle: String = js.native
  def startOfTheWeek: Int = js.native
  def locale: String = js.native

enum TimeLocale(val name: String):
  case Fi extends TimeLocale("fi")
  case Sv extends TimeLocale("sv")
  case En extends TimeLocale("en")

object TimeLocalization:
  def apply(df: DateFormats, locale: TimeLocale): TimeLocalization =
    literal(dateFormats = df, hourCycle = "h23", startOfTheWeek = 1, locale = locale.name)
      .asInstanceOf[TimeLocalization]

@js.native
trait TimeRestrictions extends js.Object:
  def minDate: js.UndefOr[Date] = js.native
  def maxDate: js.UndefOr[Date] = js.native

object TimeRestrictions:
  def apply(min: Option[Date], max: Option[Date]): TimeRestrictions =
    literal(minDate = min.orUndefined, maxDate = max.orUndefined).asInstanceOf[TimeRestrictions]

@js.native
trait IconOptions extends js.Object:
  def `type`: js.UndefOr[String] = js.native
  def close: js.UndefOr[String] = js.native
  def clear: js.UndefOr[String] = js.native
  def time: js.UndefOr[String] = js.native
  def date: js.UndefOr[String] = js.native
  def up: js.UndefOr[String] = js.native
  def down: js.UndefOr[String] = js.native
  def previous: js.UndefOr[String] = js.native
  def next: js.UndefOr[String] = js.native
  def today: js.UndefOr[String] = js.native

object IconOptions:
  def apply(
    `type`: String,
    close: String,
    clear: String,
    time: String,
    date: String,
    up: String,
    down: String,
    previous: String,
    next: String,
    today: String
  ): IconOptions =
    literal(
      `type` = `type`,
      close = close,
      clear = clear,
      time = time,
      date = date,
      up = up,
      down = down,
      previous = previous,
      next = next,
      today = today
    ).asInstanceOf[IconOptions]

@js.native
trait ButtonOptions extends js.Object:
  def clear: Boolean = js.native
  def close: Boolean = js.native

object ButtonOptions:
  def apply(clear: Boolean, close: Boolean): ButtonOptions =
    literal(clear = clear, close = close).asInstanceOf[ButtonOptions]

@js.native
trait DisplayOptions extends js.Object:
  def sideBySide: Boolean = js.native
  def icons: IconOptions = js.native
  def buttons: ButtonOptions = js.native

object DisplayOptions:
  def apply(sideBySide: Boolean, icons: IconOptions, buttons: ButtonOptions): DisplayOptions =
    literal(sideBySide = sideBySide, icons = icons, buttons = buttons).asInstanceOf[DisplayOptions]

@js.native
trait TimeOptions extends js.Object:
  def display: DisplayOptions = js.native
  def restrictions: TimeRestrictions = js.native
  def localization: TimeLocalization = js.native
  def useCurrent: Boolean = js.native

object TimeOptions:
  def apply(
    r: TimeRestrictions,
    l: TimeLocalization,
    icons: IconOptions,
    sideBySide: Boolean = false,
    useCurrent: Boolean = false,
    promptTimeOnDateChange: Boolean = true
  ) =
    val display =
      DisplayOptions(sideBySide, icons, ButtonOptions(clear = true, close = true))
    literal(
      display = display,
      restrictions = r,
      localization = l,
      useCurrent = useCurrent,
      promptTimeOnDateChange = promptTimeOnDateChange
    ).asInstanceOf[TimeOptions]

@js.native
trait TimeSubscription extends js.Object:
  def unsubscribe(): Unit = js.native

@js.native
trait BaseEvent extends js.Object:
  def `type`: String = js.native

@js.native
trait ChangeEvent extends BaseEvent:
  def date: js.UndefOr[Date] = js.native
  def isValid: Boolean = js.native
  def isClear: Boolean = js.native

@js.native
trait HideEvent extends BaseEvent:
  def date: js.UndefOr[Date] = js.native
  def viewMode: String = js.native

@js.native
trait DateTime extends js.Date

@js.native
@JSImport("@eonasdan/tempus-dominus", "DateTime")
object DateTime extends js.Object:
  def convert(date: Date): DateTime = js.native

@js.native
trait Dates extends js.Object:
  def parseInput(date: Date): DateTime
  def setValue(date: js.UndefOr[DateTime]): Unit = js.native

@js.native
@JSImport("@eonasdan/tempus-dominus", "TempusDominus")
class TempusDominus(@nowarn e: Element, @nowarn options: TimeOptions) extends js.Object:
  var viewDate: js.UndefOr[DateTime] = js.native
  def updateOptions(opts: TimeOptions, reset: Boolean): Unit = js.native
  def picked: js.UndefOr[js.Array[Date]] = js.native
  def lastPicked: js.UndefOr[Date] = js.native
  def subscribe(event: String, callback: js.Function1[BaseEvent, Unit]): TimeSubscription =
    js.native
  def parseInput(value: js.Any): DateTime = js.native
  def dates: Dates = js.native
  def clear: Unit = js.native

extension (td: TempusDominus)
  def date: Option[Date] = Option(td).flatMap(p => Option(p.viewDate)).flatMap(_.toOption)
  def updateDate(date: Option[Date]): Unit =
    val dt = date.map(DateTime.convert).orUndefined
    td.dates.setValue(dt)
