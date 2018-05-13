package com.malliina.boat

import com.malliina.boat.BoatHtml.callAttr
import com.malliina.html.Tags
import com.malliina.play.tags.TagPage
import controllers.routes
import play.api.Mode
import play.api.http.MimeTypes
import play.api.mvc.Call
import scalatags.Text.GenericAttr
import scalatags.Text.all._

object BoatHtml {
  implicit val callAttr = new GenericAttr[Call]

  def apply(mode: Mode): BoatHtml = apply(mode == Mode.Prod)

  def apply(isProd: Boolean): BoatHtml = {
    val jsFile = if (isProd) "frontend-opt.js" else "frontend-fastopt.js"
    new BoatHtml(jsFile)
  }
}

class BoatHtml(jsFile: String) extends Tags(scalatags.Text) {
  val defer = attr("defer").empty
  val reverse = routes.BoatController

  def index(msg: String) = page(PageConf(h1(msg)))

  def map = page(
    PageConf(
      div(id := "map"),
      bodyClasses = Seq(Constants.MapClass),
      cssLink("https://api.tiles.mapbox.com/mapbox-gl-js/v0.44.2/mapbox-gl.css"),
      jsScript("https://api.tiles.mapbox.com/mapbox-gl-js/v0.44.2/mapbox-gl.js")
    )
  )

  def page(content: PageConf) = TagPage(
    html(
      head(
        cssLink(reverse.versioned("css/main.css")),
        content.css,
        content.js,
        script(`type` := MimeTypes.JAVASCRIPT, defer, src := reverse.versioned(jsFile))
      ),
      body(`class` := content.bodyClasses.mkString(" "))(
        content.content
      )
    )
  )
}

case class PageConf(content: Modifier, bodyClasses: Seq[String] = Nil, css: Modifier = PageConf.empty, js: Modifier = PageConf.empty)

object PageConf {
  val empty: Modifier = ""
}
