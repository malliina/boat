package com.malliina.boat

import org.scalajs.dom

object Frontend extends BodyClasses {
  val log: BaseLogger = BaseLogger.console

  def main(args: Array[String]): Unit = {
    val bodyClasses = dom.document.body.classList

    def contains(cls: String) = bodyClasses.contains(cls)

    if (contains(MapClass)) MapView()
    if (contains(ChartsClass)) ChartsView()
    if (contains(FormsClass)) {
      FormHandlers.titles()
      FormHandlers.comments()
    }
    if (contains(AboutClass)) AboutPage()
  }
}
