package controllers

import com.malliina.boat.auth.GoogleTokenAuth
import com.malliina.boat.db.UserManager
import com.malliina.boat.{AppMeta, UserContainer}
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents

import scala.concurrent.Future

class AppController(googleAuth: GoogleTokenAuth,
                    auther: UserManager,
                    comps: ControllerComponents) extends AuthController(googleAuth, auther, comps) {
  def health = Action {
    Ok(Json.toJson(AppMeta.default))
  }

  def pingAuth = authAction(googleProfile) { _ =>
    Future.successful(Ok(Json.toJson(AppMeta.default)))
  }

  def me = authAction(profile) { req =>
    fut(Ok(UserContainer(req.user)))
  }
}
