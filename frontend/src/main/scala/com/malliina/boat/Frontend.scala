package com.malliina.boat

import org.scalajs.dom
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSImport("bootstrap/dist/css/bootstrap.min.css", JSImport.Namespace)
object BootstrapCss extends js.Object

@js.native
@JSImport("@fortawesome/fontawesome-free/css/all.min.css", JSImport.Namespace)
object FontAwesomeCss extends js.Object

@js.native
@JSImport("mapbox-gl/dist/mapbox-gl.css", JSImport.Namespace)
object MapboxCss extends js.Object

@js.native
@JSImport("@mapbox/mapbox-gl-geocoder/dist/mapbox-gl-geocoder.css", JSImport.Namespace)
object MapboxGlCss extends js.Object

object Frontend extends BodyClasses:
  private val bootstrapCss = BootstrapCss
  private val fontAwesomeCss = FontAwesomeCss
  private val mapboxCss = MapboxCss
  private val mapboxGlCss = MapboxGlCss
  val log: BaseLogger = BaseLogger.console

  def main(args: Array[String]): Unit =
    val bodyClasses = dom.document.body.classList

    def init(cls: String)(run: => Either[NotFound, Any]): Unit =
      val result = if bodyClasses.contains(cls) then run else Right(())
      result.left.foreach { notFound =>
        log.info(s"Not found: '$notFound'.")
      }

    init(MapClass) { MapView() }
    init(ChartsClass) { ChartsView() }
    init(FormsClass) {
      FormHandlers.titles().flatMap(_ => FormHandlers.comments())
    }
    init(AboutClass) {
      Right(AboutPage())
    }
    init(StatsClass) {
      Right(StatsPage())
    }
    init(BoatsClass) {
      Right(FormHandlers.inviteOthers())
    }
