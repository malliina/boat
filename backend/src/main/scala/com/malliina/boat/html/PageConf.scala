package com.malliina.boat.html

import scalatags.Text.all.*

/** @param content
  *   page content
  * @param bodyClasses
  *   body classes to toggle features
  */
case class PageConf(
  content: Modifier,
  bodyClasses: Seq[String] = Nil
)

object PageConf:
  val empty: Modifier = ""
