package com.malliina.boat.html

import scalatags.Text.all._

case class PageConf(content: Modifier,
                    bodyClasses: Seq[String] = Nil,
                    css: Modifier = PageConf.empty,
                    js: Modifier = PageConf.empty)

object PageConf {
  val empty: Modifier = ""
}
