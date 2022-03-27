package com.malliina.chartjs

import org.scalajs.dom.CanvasRenderingContext2D

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
trait Axes extends js.Object:
  def ticks: TickOptions = js.native

object Axes:
  def apply(ticks: TickOptions): Axes =
    literal(ticks = ticks).asInstanceOf[Axes]

@js.native
trait Scales extends js.Object:
  def yAxes: js.Array[Axes] = js.native

object Scales:
  def apply(yAxes: Seq[Axes]): Scales =
    literal(yAxes = yAxes.toJSArray).asInstanceOf[Scales]

@js.native
trait ChartOptions extends js.Object:
  def scales: Scales = js.native

object ChartOptions:
  def apply(scales: Scales = Scales(Nil)): ChartOptions =
    literal(scales = scales).asInstanceOf[ChartOptions]

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
    options: ChartOptions = ChartOptions()
  ): ChartSpecs =
    literal(`type` = typeValue, data = data, options = options).asInstanceOf[ChartSpecs]

@js.native
@JSImport("chart.js", "Chart")
class Chart(ctx: CanvasRenderingContext2D, options: ChartSpecs) extends js.Object:
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
