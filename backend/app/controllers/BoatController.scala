package controllers

import java.time.Instant

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, Sink, Source}
import com.malliina.boat.db.UserManager
import com.malliina.boat.parsing.BoatParser.{parseCoords, read}
import com.malliina.boat.{AccessToken, BoatEvent, BoatHtml, BoatInfo, BoatName, BoatNames, Constants, Errors, FrontEvent, PingEvent, SentencesMessage, Streams, TrackName, TrackNames, User}
import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.play.auth.Auth
import com.malliina.play.models.Username
import controllers.Assets.Asset
import controllers.BoatController.{BoatNameHeader, TrackNameHeader, log}
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.mvc.WebSocket.MessageFlowTransformer.jsonMessageFlowTransformer
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object BoatController {
  private val log = Logger(getClass)

  val BoatNameHeader = "X-Boat"
  val TrackNameHeader = "X-Track"
}

class BoatController(mapboxToken: AccessToken,
                     html: BoatHtml,
                     auth: UserManager,
                     comps: ControllerComponents,
                     assets: AssetsBuilder)(implicit as: ActorSystem, mat: Materializer)
  extends AbstractController(comps) with Streams {

  implicit val updatesTransformer = jsonMessageFlowTransformer[JsValue, FrontEvent]
  val anonUser = User("anon")

  // Publish-Subscribe Akka Streams
  // https://doc.akka.io/docs/akka/2.5/stream/stream-dynamic.html
  val (boatSink, viewerSource) = MergeHub.source[BoatEvent](perProducerBufferSize = 16)
    .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
    .run()

  val _ = viewerSource.runWith(Sink.ignore)
  val sentencesSource = viewerSource.map { boatEvent =>
    read[SentencesMessage](boatEvent.message).map(_.toEvent(boatEvent.from))
  }
  val parsedEvents = sentencesSource.map(e => e.map(parseCoords))
  val sentences = rights(sentencesSource)
  val coords = rights(parsedEvents)
  val errors = lefts(parsedEvents)

  errors.runWith(Sink.foreach(err => log.error(s"JSON error: '$err'.")))

  def index = Action(Ok(html.map).withCookies(Cookie(Constants.TokenCookieName, mapboxToken.token, httpOnly = false)))

  def boats = WebSocket { rh =>
    auth(rh).map { e =>
      e.right.map { user =>
        val boatName = rh.headers.get(BoatNameHeader).map(BoatName.apply).getOrElse(BoatNames.random())
        val trackName = rh.headers.get(TrackNameHeader).map(TrackName.apply).getOrElse(TrackNames.random())
        val info = BoatInfo(user, boatName, trackName)
        // adds metadata to messages from boats
        val transformer = jsonMessageFlowTransformer.map[BoatEvent, JsValue](
          json => BoatEvent(json, info),
          out => out
        )
        transformer.transform(Flow.fromSinkAndSource(boatSink, Source.maybe[JsValue]))
      }
    }
  }

  def updates = WebSocket.acceptOrResult[JsValue, FrontEvent] { rh =>
    auth(rh).map { outcome =>
      outcome.map { user =>
        val events: Source[FrontEvent, NotUsed] = sentences.merge(coords)
        // disconnects viewers that lag more than 3s
        Flow.fromSinkAndSource(Sink.ignore, events.filter(_.isIntendedFor(user)))
          .keepAlive(10.seconds, () => PingEvent(Instant.now.toEpochMilli))
          .backpressureTimeout(3.seconds)
      }
    }
  }

  def registerTrack(user: Username, boat: BoatName, track: TrackName) = Action(Ok)

  def auth(rh: RequestHeader): Future[Either[Result, User]] =
    Auth.basicCredentials(rh).map { creds =>
      auth.authenticate(User(creds.username.name), creds.password).map { outcome =>
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
