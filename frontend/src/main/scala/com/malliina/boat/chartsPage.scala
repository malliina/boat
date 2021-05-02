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
  readTrack.toOption.foreach { track =>
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
  extends BoatSocket(Name(track), sample) {

  val seaBlue = "#006994"
  val red = "red"

  val depthLabel = "Depth"
  val speedLabel = "Speed"

  val depths = dataSet(depthLabel, seaBlue)
  val speeds = dataSet(speedLabel, red)
  val chartData = ChartData(Nil, Seq(depths, speeds))
  ChartObj.register(CategoryScale, LineController, LineElement, PointElement, LinearScale, Title)
  val chart = Chart(ctx, ChartSpecs.line(chartData))

  private def dataSet(label: String, color: String) = DataSet(
    label = label,
    fill = Option(false),
    data = Nil,
    backgroundColor = Seq(color),
    pointRadius = 1,
    borderColor = Seq(color),
    borderWidth = 2
  )

  override def onCoords(event: CoordsEvent): Unit = {
    val coords = event.coords
    chart.data.append(
      coords.map(_.boatTimeOnly.time),
      Map(
        depthLabel -> coords.map(_.depthMeters.toMeters),
        speedLabel -> coords.map(c => math.rint(c.speed.toKnots * 100) / 100)
      )
    )
    chart.update()
  }

  override def onGps(event: GPSCoordsEvent): Unit = ()

  override def onAIS(messages: Seq[VesselInfo]): Unit = ()
}

case class ChartValue(label: String, value: Double)
