package com.malliina.boat.html

import scalatags.Text.all._

case class PageConf(content: Modifier,
                    bodyClasses: Seq[String] = Nil,
                    scriptsAndStyles: Modifier = PageConf.empty)

object PageConf {
  val empty: Modifier = ""
}
