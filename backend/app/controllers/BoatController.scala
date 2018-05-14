package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.malliina.boat.db.UserManager
import com.malliina.boat.ws.BoatActor.BoatClient
import com.malliina.boat.ws.ViewerActor.ViewerClient
import com.malliina.boat.ws.{BoatActor, BoatManager, ViewerActor, ViewerManager}
import com.malliina.boat.{BoatHtml, BoatName, BoatNames, Constants, Errors, TrackName, TrackNames}
import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.play.auth.Auth
import com.malliina.play.models.Username
import controllers.Assets.Asset
import controllers.BoatController.{BoatNameHeader, TrackNameHeader, log}
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc._

import scala.concurrent.Future

object BoatController {
  private val log = Logger(getClass)

  val BoatNameHeader = "X-Boat"
  val TrackNameHeader = "X-Track"
}

class BoatController(mapboxToken: String,
                     html: BoatHtml,
                     auth: UserManager,
                     comps: ControllerComponents,
                     assets: AssetsBuilder)(implicit as: ActorSystem, mat: Materializer)
  extends AbstractController(comps) {

  val viewerManager = as.actorOf(ViewerManager.props())
  val boatManager = as.actorOf(BoatManager.props(viewerManager, mat))
  val anonUser = Username("anon")

  def index = Action(Ok(html.map).withCookies(Cookie(Constants.TokenCookieName, mapboxToken, httpOnly = false)))

  def updates = WebSocket.acceptOrResult[JsValue, JsValue] { rh =>
    auth(rh).map { outcome =>
      outcome.map { user =>
        ActorFlow.actorRef { out =>
          ViewerActor.props(ViewerClient(user, viewerManager, out, rh))
        }
      }
    }
  }

  def registerTrack(user: Username, boat: BoatName, track: TrackName) = Action(Ok)

  def boats = WebSocket.acceptOrResult[JsValue, JsValue] { rh =>
    auth(rh).map { outcome =>
      outcome.map { user =>
        ActorFlow.actorRef { out =>
          val boatName = rh.getQueryString(BoatNameHeader).map(BoatName.apply).getOrElse(BoatNames.random())
          val trackName = rh.getQueryString(TrackNameHeader).map(TrackName.apply).getOrElse(TrackNames.random())
          BoatActor.props(BoatClient(user, boatName, trackName, boatManager, out, rh))
        }
      }
    }
  }

  def auth(rh: RequestHeader): Future[Either[Result, Username]] =
    Auth.basicCredentials(rh).map { creds =>
      auth.authenticate(creds.username, creds.password).map { outcome =>
        outcome.map { profile =>
          Right(profile.username)
        }.recover { err =>
          log.warn(s"Authentication failed from '$rh': '$err'.")
          Left(Unauthorized(Errors("Unauthorized.")))
        }
      }
    }.getOrElse {
      //      Future.successful(Left(Unauthorized(Errors("Credentials required."))))
      Future.successful(Right(anonUser))
    }

  def versioned(path: String, file: Asset): Action[AnyContent] =
    assets.versioned(path, file)
}
