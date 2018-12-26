package com.malliina.boat

import org.scalajs.dom

object Frontend extends BodyClasses {
  val log: BaseLogger = BaseLogger.console

  def main(args: Array[String]): Unit = {
    val bodyClasses = dom.document.body.classList

    def contains(cls: String) = bodyClasses.contains(cls)

    val init: Either[NotFound, _] =
      if (contains(MapClass)) MapView()
      else if (contains(ChartsClass)) ChartsView()
      else if (contains(ListClass)) ListView()
      else Right(())
    init.left.foreach { notFound =>
      log.info(s"Initialization error. Not found: '${notFound.id}'.")
    }
  }
}
