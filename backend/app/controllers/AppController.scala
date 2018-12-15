package controllers

import com.malliina.boat.auth.EmailAuth
import com.malliina.boat.db.UserManager
import com.malliina.boat.{AppMeta, UserContainer}
import controllers.Assets.Asset
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class AppController(googleAuth: EmailAuth,
                    auther: UserManager,
                    assets: AssetsBuilder,
                    comps: ControllerComponents) extends AuthController(googleAuth, auther, comps) {
  def health = Action {
    Ok(Json.toJson(AppMeta.default))
  }

  def pingAuth = authAction(googleProfile) { _ =>
    fut(Ok(Json.toJson(AppMeta.default)))
  }

  def me = authAction(profile) { req =>
    fut(Ok(UserContainer(req.user)))
  }

  def versioned(path: String, file: Asset): Action[AnyContent] =
    assets.versioned(path, file)
}
