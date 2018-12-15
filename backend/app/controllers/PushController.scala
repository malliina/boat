package controllers

import com.malliina.boat.auth.EmailAuth
import com.malliina.boat.db.{PushDatabase, PushInput, UserManager}
import com.malliina.boat.http.BoatRequest
import com.malliina.boat.{PushPayload, SimpleMessage, SingleToken, UserInfo}
import play.api.libs.json.Reads
import play.api.mvc.{ControllerComponents, Result}

import scala.concurrent.Future

class PushController(push: PushDatabase,
                     googleAuth: EmailAuth,
                     auther: UserManager,
                     comps: ControllerComponents) extends AuthController(googleAuth, auther, comps) {
  def enableNotifications = jsonAuth[PushPayload] { req =>
    val payload = req.body
    push.enable(PushInput(payload.token, payload.device, req.user.id)).map { _ =>
      Ok(SimpleMessage("enabled"))
    }
  }

  def disableNotifications = jsonAuth[SingleToken] { req =>
    push.disable(req.body.token, req.user.id).map { disabled =>
      val msg = if (disabled) "disabled" else "no change"
      Ok(SimpleMessage(msg))
    }
  }

  def jsonAuth[R: Reads](code: BoatRequest[UserInfo, R] => Future[Result]) =
    parsedAuth(parse.json[R])(profile) { req =>
      code(req)
    }
}
