package com.malliina.boat

import org.scalajs.dom

import scala.annotation.unused
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

object Frontend extends BodyClasses:
  @unused
  private val bootstrapCss = BootstrapCss
  @unused
  private val mapboxCss = MapboxCss
  @unused
  private val mapboxGlCss = MapboxGlCss
  @unused
  private val tempusDominusCss = TempusDominusCss
  val log: BaseLogger = BaseLogger.console

  def main(args: Array[String]): Unit =
    init(MapClass) { MapView.default }
    init(ChartsClass) { ChartsView.default }
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

  def init(cls: String)(run: => Either[NotFound, Any]): Unit =
    val bodyClasses = dom.document.body.classList
    val result = if bodyClasses.contains(cls) then run else Right(())
    result.left.foreach { notFound =>
      log.info(s"Not found: '$notFound'.")
    }
