package controllers

import com.malliina.boat.auth.EmailAuth
import com.malliina.boat.db.UserManager
import com.malliina.boat.{AppMeta, ClientConf, UserContainer}
import controllers.Assets.Asset
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class AppController(
  googleAuth: EmailAuth,
  auther: UserManager,
  assets: AssetsBuilder,
  comps: ControllerComponents
) extends AuthController(googleAuth, auther, comps) {

  def health = Action {
    Ok(Json.toJson(AppMeta.default))
  }

  def pingAuth = authAction(profile) { _ =>
    fut(Ok(Json.toJson(AppMeta.default)))
  }

  def me = authAction(profile) { req =>
    fut(Ok(UserContainer(req.user)))
  }

  def conf = Action { Ok(Json.toJson(ClientConf.default)) }

  /** Controller for webpack-fingerprinted static assets. Check webpack.*.config.js for the
    * matching path definition.
    */
  def static(file: String): Action[AnyContent] =
    assets.at("/public", s"static/$file", aggressiveCaching = true)

  def versioned(path: String, file: Asset): Action[AnyContent] =
    assets.versioned(path, file)

  def appleAppSiteAssociation = assets.at("/public", "apple-app-site-association.json")
  def androidAppLink = assets.at("/public", "android-assetlinks.json")
}
