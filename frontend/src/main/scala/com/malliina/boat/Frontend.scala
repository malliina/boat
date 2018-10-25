package com.malliina.boat

import com.malliina.boat.FrontKeys.{ChartsClass, MapClass}
import org.scalajs.dom

object Frontend {
  def main(args: Array[String]): Unit = {
    val bodyClasses = dom.document.body.classList
    if (bodyClasses.contains(MapClass)) MapView()
    else if (bodyClasses.contains(ChartsClass)) ChartsView()
    else ()
  }
}
