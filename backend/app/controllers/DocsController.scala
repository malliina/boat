package controllers

import com.malliina.http.FullUrl
import play.api.mvc.{AbstractController, ControllerComponents}

class DocsController(comps: ControllerComponents)
  extends AbstractController(comps) {

  def agent = redirect("agent")

  def support = redirect("support")

  def privacyPolicy = redirect("privacy")

  def redirect(path: String) = Action {
    Redirect(FullUrl.https("docs.boat-tracker.com", s"/$path/").url)
  }
}
