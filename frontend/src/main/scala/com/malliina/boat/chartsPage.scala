package com.malliina.boat

import com.malliina.chartjs._
import org.scalajs.dom.raw.{CanvasRenderingContext2D, HTMLCanvasElement}

object ChartsView extends BaseFront {
  def apply(): Either[NotFound, ChartsView] = elem(ChartsId).map { e =>
    new ChartsView(e.asInstanceOf[HTMLCanvasElement])
  }
}

class ChartsView(canvas: HTMLCanvasElement) extends BaseFront {
  val ctx = canvas.getContext("2d").asInstanceOf[CanvasRenderingContext2D]
  readTrack.foreach { track =>
    val sample = queryInt(SampleKey)
    ChartSocket(ctx, track, sample)
  }
}

object ChartSocket {
  def apply(ctx: CanvasRenderingContext2D, track: TrackName, sample: Option[Int]): ChartSocket =
    new ChartSocket(ctx, track, sample)
}

/** Initializes an empty chart, then appends data in `onCoords`.
  *
  * @param ctx    canvas
  * @param track  track
  * @param sample 1 = full accuracy, None = intelligent
  */
class ChartSocket(ctx: CanvasRenderingContext2D, track: TrackName, sample: Option[Int])
  extends BoatSocket(Option(track), sample) {

  val seaBlue = "#006994"
  val red = "red"

  val depthLabel = "Depth"
  val speedLabel = "Speed"

  val depths = DataSet(
    label = depthLabel,
    fill = Option(false),
    data = Nil,
    backgroundColor = Seq(seaBlue),
    pointRadius = 1,
    borderColor = Seq(seaBlue),
    borderWidth = 2
  )
  val speeds = DataSet(
    label = speedLabel,
    fill = Option(false),
    data = Nil,
    backgroundColor = Seq(red),
    pointRadius = 1,
    borderColor = Seq(red),
    borderWidth = 2
  )
  val chartData = ChartData(Nil, Seq(depths, speeds))
  val chart = Chart(ctx, ChartSpecs.line(chartData))

  override def onCoords(event: CoordsEvent): Unit = {
    val coords = event.coords
    chart.data.append(coords.map(_.boatTimeOnly), Map(
      depthLabel -> coords.map(_.depth.toMetersDouble),
      speedLabel -> coords.map(c => math.rint(c.speed.toKnotsDouble * 100) / 100)
    ))
    chart.update()
  }
}

case class ChartValue(label: String, value: Double)
