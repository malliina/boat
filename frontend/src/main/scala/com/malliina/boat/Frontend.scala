package com.malliina.boat

import org.scalajs.dom

object Frontend {
  def main(args: Array[String]): Unit = {
    val bodyClasses = dom.document.body.classList
    if (bodyClasses.contains(Constants.MapClass)) MapView()
  }
}
