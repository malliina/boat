package com.malliina.chartjs

import org.scalajs.dom.CanvasRenderingContext2D

import scala.annotation.unused
import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.annotation.{JSImport, JSName}

@js.native
trait TickOptions extends js.Object:
  def beginAtZero: Boolean = js.native

object TickOptions:
  def apply(beginAtZero: Boolean = false): TickOptions =
    literal(beginAtZero = beginAtZero).asInstanceOf[TickOptions]

@js.native
trait StackedAxes extends js.Object:
  def stacked: Boolean = js.native

object StackedAxes:
  def apply(stacked: Boolean): StackedAxes =
    literal(stacked = stacked).asInstanceOf[StackedAxes]

@js.native
trait ChartTitle extends js.Object:
  def display: Boolean = js.native
  def text: String = js.native

object ChartTitle:
  def apply(display: Boolean, text: String): ChartTitle =
    literal(display = display, text = text).asInstanceOf[ChartTitle]

@js.native
trait Axes extends js.Object:
  def title: ChartTitle = js.native
  def ticks: TickOptions = js.native

object Axes:
  def apply(title: ChartTitle, ticks: TickOptions): Axes =
    literal(title = title, ticks = ticks).asInstanceOf[Axes]

@js.native
trait Scales extends js.Object:
  def y: js.UndefOr[Axes] = js.native

object Scales:
  def apply(y: Option[Axes]): Scales =
    y.fold(literal())(spec => literal(y = spec)).asInstanceOf[Scales]

@js.native
trait ChartLegend extends js.Object:
  def display: Boolean = js.native

object ChartLegend:
  def apply(display: Boolean): ChartLegend = literal(display = display).asInstanceOf[ChartLegend]

@js.native
trait TooltipContext extends js.Object:
  def chart: js.Any = js.native
  def tooltip: js.Any = js.native

@js.native
trait ChartTooltip extends js.Object:
  def enabled: Boolean = js.native
  def position: String = js.native
  def external: js.Function1[TooltipContext, Any] = js.native

object ChartTooltip:
  val Average = "average"
  val Nearest = "nearest"

  def apply(
    enabled: Boolean,
    external: TooltipContext => Any,
    position: String = Nearest
  ): ChartTooltip =
    literal(enabled = enabled, external = external, position = position).asInstanceOf[ChartTooltip]

@js.native
trait ChartPlugins extends js.Object:
  def title: ChartTitle
  def legend: ChartLegend
  def tooltip: ChartTooltip

object ChartPlugins:
  def configure(
    title: ChartTitle,
    legend: Boolean,
    tooltip: Boolean,
    external: TooltipContext => Any
  ): ChartPlugins =
    apply(title, ChartLegend(legend), ChartTooltip(tooltip, external))

  def apply(title: ChartTitle, legend: ChartLegend, tooltip: ChartTooltip): ChartPlugins =
    literal(title = title, legend = legend, tooltip = tooltip).asInstanceOf[ChartPlugins]

@js.native
trait ChartOptions extends js.Object:
  def scales: Scales = js.native
  def responsive: Boolean = js.native
  def maintainAspectRatio: Boolean = js.native
  def plugins: ChartPlugins = js.native

object ChartOptions:
  def apply(
    plugins: ChartPlugins,
//    scales: Scales = Scales(Option(Axes(AxesTitle(true, "Knots or Depth"), TickOptions()))),
    scales: Scales = Scales(None),
    responsive: Boolean = true,
    maintainAspectRatio: Boolean = true
  ): ChartOptions =
    literal(
      plugins = plugins,
      scales = scales,
      responsive = responsive,
      maintainAspectRatio = maintainAspectRatio
    )
      .asInstanceOf[ChartOptions]

@js.native
trait DataSet extends js.Object:
  def label: String = js.native
  def data: js.Array[Double] = js.native
  def backgroundColor: js.Array[String] = js.native
  def borderColor: js.Array[String] = js.native
  def borderWidth: Int = js.native
  def pointRadius: Int = js.native
  def fill: js.UndefOr[Boolean] = js.native
  def lineTension: Double = js.native

object DataSet:
  def apply(
    label: String,
    data: Seq[Double],
    fill: Option[Boolean] = None,
    backgroundColor: Seq[String] = Nil,
    borderColor: Seq[String] = Nil,
    borderWidth: Int = 1,
    pointRadius: Int = 3,
    lineTension: Double = 0.4
  ): DataSet =
    val fillValue: js.Any = fill.map(b => b: js.Any).getOrElse(js.undefined)
    literal(
      label = label,
      data = data.toJSArray,
      fill = fillValue,
      backgroundColor = backgroundColor.toJSArray,
      borderColor = borderColor.toJSArray,
      borderWidth = borderWidth,
      pointRadius = pointRadius,
      lineTension = lineTension
    ).asInstanceOf[DataSet]

@js.native
trait ChartData extends js.Object:
  def labels: js.Array[String] = js.native
  def datasets: js.Array[DataSet] = js.native

object ChartData:
  def apply(labels: Seq[String], datasets: Seq[DataSet]): ChartData =
    literal(labels = labels.toJSArray, datasets = datasets.toJSArray).asInstanceOf[ChartData]

  implicit class ChartDataOps(val self: ChartData) extends AnyVal:
    def append(labels: Seq[String], datasets: Map[String, Seq[Double]]): Unit =
      self.labels.push(labels*)
      for
        (label, values) <- datasets
        dataset <- self.datasets.find(_.label == label).toSeq
      do dataset.data.push(values*)

@js.native
trait ChartSpecs extends js.Object:
  @JSName("type")
  def `type`: String = js.native
  def data: ChartData = js.native
  def options: ChartOptions = js.native

object ChartSpecs:
  def line(data: ChartData) = apply("line", data)

  def apply(
    typeValue: String,
    data: ChartData,
    options: ChartOptions = ChartOptions(
      ChartPlugins.configure(
        ChartTitle(false, ""),
        true,
        true,
        ctx => {}
      )
    )
  ): ChartSpecs =
    literal(`type` = typeValue, data = data, options = options).asInstanceOf[ChartSpecs]

@js.native
@JSImport("chart.js", "Chart")
class Chart(@unused ctx: CanvasRenderingContext2D, @unused options: ChartSpecs) extends js.Object:
  def data: ChartData = js.native
  def update(): Unit = js.native

object Chart:
  def apply(ctx: CanvasRenderingContext2D, options: ChartSpecs): Chart =
    new Chart(ctx, options)

@js.native
@JSImport("chart.js", "Chart")
object ChartObj extends js.Object:
  def register(obj: js.Object*): Unit = js.native

@js.native
@JSImport("chart.js", "LinearScale")
object LinearScale extends js.Object

@js.native
@JSImport("chart.js", "LineElement")
object LineElement extends js.Object

@js.native
@JSImport("chart.js", "LineController")
object LineController extends js.Object

@js.native
@JSImport("chart.js", "Title")
object Title extends js.Object

@js.native
@JSImport("chart.js", "PointElement")
object PointElement extends js.Object

@js.native
@JSImport("chart.js", "CategoryScale")
object CategoryScale extends js.Object

@js.native
@JSImport("chart.js", "Tooltip")
object Tooltip extends js.Object
