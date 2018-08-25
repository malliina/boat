package controllers

import com.malliina.boat.html.BoatHtml
import play.api.mvc.{AbstractController, ControllerComponents}

class DocsController(html: BoatHtml, comps: ControllerComponents)
  extends AbstractController(comps) {

  def agent = Action {
    Ok(html.docs)
  }

  def privacyPolicy = Action {
    Ok(html.privacyPolicy)
  }
}
