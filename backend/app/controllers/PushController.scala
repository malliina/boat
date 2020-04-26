package controllers

import com.malliina.boat.auth.EmailAuth
import com.malliina.boat.db.{PushInput, UserManager}
import com.malliina.boat.http.UserRequest
import com.malliina.boat.push.PushService
import com.malliina.boat.{ChangeLanguage, PushPayload, SimpleMessage, SingleToken, UserInfo}
import play.api.libs.json.Reads
import play.api.mvc.{ControllerComponents, Result}

import scala.concurrent.Future

class PushController(
  push: PushService,
  googleAuth: EmailAuth,
  auther: UserManager,
  comps: ControllerComponents
) extends AuthController(googleAuth, auther, comps) {
  val NoChange = "No change."

  def enableNotifications = jsonAuth[PushPayload] { req =>
    val payload = req.body
    push.enable(PushInput(payload.token, payload.device, req.user.id)).map { _ =>
      Ok(SimpleMessage("Enabled."))
    }
  }

  def disableNotifications = jsonAuth[SingleToken] { req =>
    push.disable(req.body.token, req.user.id).map { disabled =>
      val msg = if (disabled) "Disabled." else NoChange
      Ok(SimpleMessage(msg))
    }
  }

  def changeLanguage = jsonAuth[ChangeLanguage] { req =>
    val newLanguage = req.body.language
    auther.changeLanguage(req.user.id, newLanguage).map { changed =>
      val msg = if (changed) s"Changed language to $newLanguage." else NoChange
      Ok(SimpleMessage(msg))
    }
  }

  def jsonAuth[R: Reads](code: UserRequest[UserInfo, R] => Future[Result]) =
    parsedAuth(parse.json[R])(profile) { req =>
      code(req)
    }
}
