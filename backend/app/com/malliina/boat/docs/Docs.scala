package com.malliina.boat.docs

import com.malliina.file.FileUtilities
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.options.MutableDataSet
import scalatags.Text.RawFrag

object Docs extends Docs {
  def agent: RawFrag = fromFile("Agent")

  def privacyPolicy: RawFrag = fromFile("PrivacyPolicy")
}

trait Docs {
  def fromFile(file: String) = toHtml(markdownAsString(file))

  def markdownAsString(docName: String) =
    FileUtilities.readerFrom(s"docs/$docName.md")(_.mkString(FileUtilities.lineSep))

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
