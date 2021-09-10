package com.malliina.boat

import org.scalajs.dom

object Frontend extends BodyClasses:
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
