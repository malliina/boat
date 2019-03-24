package com.malliina.boat.html

import scalatags.Text.all._

/**
  * @param content page content
  * @param bodyClasses body classes to toggle features
  * @param scriptsAndStyles css and js for the page
  * @param lateStyles styles defined after app JavaScript definitions
  */
case class PageConf(content: Modifier,
                    bodyClasses: Seq[String] = Nil,
                    scriptsAndStyles: Modifier = PageConf.empty,
                    lateStyles: Modifier = PageConf.empty)

object PageConf {
  val empty: Modifier = ""
}
