package com.malliina.boat

import cats.effect.std.Dispatcher
import cats.effect.{IO, IOApp, Resource}
import com.malliina.http.{Http, HttpClient}
import fs2.concurrent.Topic
import org.scalajs.dom
import org.scalajs.dom.MessageEvent

import scala.annotation.unused
import scala.concurrent.duration.Duration
import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSImport("bootstrap/dist/css/bootstrap.min.css", JSImport.Namespace)
object BootstrapCss extends js.Object

@js.native
@JSImport("mapbox-gl/dist/mapbox-gl.css", JSImport.Namespace)
object MapboxCss extends js.Object

@js.native
@JSImport("@mapbox/mapbox-gl-geocoder/dist/mapbox-gl-geocoder.css", JSImport.Namespace)
object MapboxGlCss extends js.Object

@js.native
@JSImport("@eonasdan/tempus-dominus/dist/css/tempus-dominus.css", JSImport.Namespace)
object TempusDominusCss extends js.Object

object Frontend extends IOApp.Simple with BodyClasses:
  override def runtimeConfig =
    super.runtimeConfig.copy(cpuStarvationCheckInitialDelay = Duration.Inf)
  @unused
  private val bootstrapCss = BootstrapCss
  @unused
  private val mapboxCss = MapboxCss
  @unused
  private val mapboxGlCss = MapboxGlCss
  @unused
  private val tempusDominusCss = TempusDominusCss
  val log: BaseLogger = BaseLogger.console

  override def run: IO[Unit] =
    val client = HttpClient[IO]()
    val resource = for
      dispatcher <- Dispatcher.parallel[IO]
      http = Http(client, dispatcher)
      forms = FormHandlers(http)
      messages <- Resource.eval(Topic[IO, MessageEvent])
      _ <- initF(MapClass):
        for
          map <- MapView.default[IO](messages, http)
          _ <- map.runnables.runInBackground
        yield ()
      _ <- initF(ChartsClass):
        ChartsView
          .default(messages, dispatcher)
          .map(_.task)
          .getOrElse(fs2.Stream.empty)
          .runInBackground
      _ <- init(FormsClass):
        forms.titles().flatMap(_ => forms.comments())
      _ <- init(AboutClass):
        Right(AboutPage(http))
      _ <- init(StatsClass):
        Right(StatsPage())
      _ <- init(BoatsClass):
        Right(forms.inviteOthers())
    yield ()
    resource.useForever.onError: t =>
      IO.delay(log.info(s"Initialization error: $t."))

  private def init(cls: String)(run: => Either[NotFound, Any]): Resource[IO, Unit] =
    initF(cls):
      Resource.eval(
        IO.delay(run)
          .flatMap: e =>
            e.fold(nf => IO.raiseError(Exception(s"Not found: '${nf.id}'.")), _ => IO.unit)
      )

  private def initF(cls: String)(run: => Resource[IO, Unit]): Resource[IO, Unit] =
    val bodyClasses = dom.document.body.classList
    if bodyClasses.contains(cls) then run else Resource.unit[IO]
