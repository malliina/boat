package com.malliina.boat

import com.malliina.chartjs._
import org.scalajs.dom.raw.{CanvasRenderingContext2D, HTMLCanvasElement}

object ChartsView {
  def apply() = new ChartsView
}

class ChartsView extends BaseFront {
  elem(ChartsId).foreach { e =>
    val ctx = e.asInstanceOf[HTMLCanvasElement].getContext("2d").asInstanceOf[CanvasRenderingContext2D]
    val track = TrackName(href.getPath.split('/')(2))
    val sample = queryInt(SampleKey)
    ChartSocket(ctx, track, sample)
  }
}

object ChartSocket {
  def apply(ctx: CanvasRenderingContext2D, track: TrackName, sample: Option[Int]): ChartSocket = {
    val sampleQuery = sample.map(s => s"&${FrontKeys.SampleKey}=$s").getOrElse("")
    new ChartSocket(ctx, s"/ws/updates?track=$track$sampleQuery")
  }
}

class ChartSocket(ctx: CanvasRenderingContext2D, wsPath: String)
  extends BoatSocket(wsPath) {

  val seaBlue = "#006994"
  val red = "red"

  override def onCoords(event: CoordsEvent): Unit = {
    val coords = event.coords
    val depths = DataSet(
      label = "Depth",
      fill = Option(false),
      data = coords.map(_.depth.toMetersDouble),
      backgroundColor = Seq(seaBlue),
      pointRadius = 1,
      borderColor = Seq(seaBlue),
      borderWidth = 2
    )
    val speeds = DataSet(
      label = "Speed",
      fill = Option(false),
      data = coords.map(c => math.rint(c.speed.toKnotsDouble * 100) / 100),
      backgroundColor = Seq(red),
      pointRadius = 1,
      borderColor = Seq(red),
      borderWidth = 2
    )
    val chartData = ChartData(coords.map(_.boatTimeOnly), Seq(depths, speeds))
    Chart(ctx, ChartSpecs.line(chartData))
  }
}

case class ChartValue(label: String, value: Double)
