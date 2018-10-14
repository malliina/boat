package com.malliina.boat.docs

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.options.MutableDataSet
import scalatags.Text.RawFrag

import scala.io.Source

object Docs extends Docs {
  def agent: RawFrag = fromFile("Agent")

  def support: RawFrag = fromFile("Support")

  def privacyPolicy: RawFrag = fromFile("PrivacyPolicy")
}

trait Docs {
  val lineSep = sys.props("line.separator")

  def fromFile(file: String) = toHtml(markdownAsString(file))

  def markdownAsString(docName: String): String =
    Source.fromResource(s"docs/$docName.md").getLines().toList.mkString(lineSep)

  /**
    * @param markdownSource markdown
    * @return HTML
    */
  def toHtml(markdownSource: String): RawFrag = {
    val options = new MutableDataSet()
    val parser = Parser.builder(options).build()
    val renderer = HtmlRenderer.builder(options).build()
    val doc = parser.parse(markdownSource)
    RawFrag(renderer.render(doc))
  }
}
