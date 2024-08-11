package com.malliina.boat

import cats.effect.{Concurrent, Sync}
import cats.effect.std.Dispatcher
import com.malliina.chartjs.*
import fs2.Stream
import fs2.concurrent.Topic
import org.scalajs.dom.{CanvasRenderingContext2D, HTMLCanvasElement}

object ChartsView extends BaseFront:
  def default[F[_]: Sync: Concurrent](
                                       messages: Topic[F, WebSocketEvent],
                                       d: Dispatcher[F]
  ): Either[NotFound, ChartsView[F]] =
    elemAs[HTMLCanvasElement](ChartsId).map: canvas =>
      ChartsView(canvas, messages, d)

class ChartsView[F[_]: Sync: Concurrent](
                                          canvas: HTMLCanvasElement,
                                          messages: Topic[F, WebSocketEvent],
                                          d: Dispatcher[F]
) extends BaseFront:
  val ctx = canvas.getContext("2d").asInstanceOf[CanvasRenderingContext2D]
  val socket = parseUri.toOption.map: track =>
    val sample = queryInt(SampleKey)
    ChartSocket(ctx, track, sample, messages, d)
  val task = socket.map(_.task).getOrElse(Stream.empty)

/** Initializes an empty chart, then appends data in `onCoords`.
  *
  * @param ctx
  *   canvas
  * @param track
  *   track
  * @param sample
  *   1 = full accuracy, None = intelligent
  */
class ChartSocket[F[_]: Sync: Concurrent](
                                           ctx: CanvasRenderingContext2D,
                                           track: TrackName,
                                           sample: Option[Int],
                                           messages: Topic[F, WebSocketEvent],
                                           d: Dispatcher[F]
) extends BoatSocket(Name(track), sample, messages, d):
  val events = Events(messages)
  val task = events.coordEvents
    .concurrently(events.connectivityLogger)
    .tap: event =>
      handleCoords(event)

  private val seaBlue = "#006994"
  private val red = "red"

  private val depthLabel = "Depth"
  private val speedLabel = "Speed"

  private val depths = dataSet(depthLabel, seaBlue)
  private val speeds = dataSet(speedLabel, red)
  private val chartData = ChartData(Nil, Seq(depths, speeds))
  ChartObj.register(
    CategoryScale,
    LineController,
    LineElement,
    PointElement,
    LinearScale,
    Title,
    Tooltip
  )
  private val chart = Chart(ctx, ChartSpecs.line(chartData))

  private def dataSet(label: String, color: String) = DataSet(
    label = label,
    fill = Option(false),
    data = Nil,
    backgroundColor = Seq(color),
    pointRadius = 1,
    borderColor = Seq(color),
    borderWidth = 2
  )

  private def handleCoords(event: CoordsEvent): Unit =
    val coords = event.coords
    chart.data.append(
      coords.map(_.boatTimeOnly.time),
      Map(
        depthLabel -> coords.map(_.depthMeters.toMeters),
        speedLabel -> coords.map(c => math.rint(c.speed.toKnots * 100) / 100)
      )
    )
    chart.update()
