package controllers

import java.time.Instant

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, Sink, Source}
import com.malliina.boat._
import com.malliina.boat.db.{TracksSource, UserManager}
import com.malliina.boat.parsing.BoatParser.{parseCoords, read}
import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.play.auth.Auth
import com.malliina.play.models.Username
import controllers.Assets.Asset
import controllers.BoatController.{BoatNameHeader, BoatTokenHeader, anonUser, log}
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.mvc.WebSocket.MessageFlowTransformer.jsonMessageFlowTransformer
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object BoatController {
  private val log = Logger(getClass)

  val BoatNameHeader = "X-Boat"
  val BoatTokenHeader = "X-Token"
  val TrackNameHeader = "X-Track"

  val anonUser = User("anon")
}

class BoatController(mapboxToken: AccessToken,
                     html: BoatHtml,
                     auther: UserManager,
                     db: TracksSource,
                     comps: ControllerComponents,
                     assets: AssetsBuilder)(implicit as: ActorSystem, mat: Materializer)
  extends AbstractController(comps) with Streams {

  implicit val updatesTransformer = jsonMessageFlowTransformer[JsValue, FrontEvent]

  // Publish-Subscribe Akka Streams
  // https://doc.akka.io/docs/akka/2.5/stream/stream-dynamic.html
  val (boatSink, viewerSource) = MergeHub.source[BoatEvent](perProducerBufferSize = 16)
    .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
    .run()

  val _ = viewerSource.runWith(Sink.ignore)
  val sentencesSource = viewerSource.map { boatEvent =>
    read[SentencesMessage](boatEvent.message)
      .map(_.toEvent(boatEvent.from))
      .left.map(err => BoatJsonError(err, boatEvent))
  }
  val parsedEvents = sentencesSource.map(e => e.map(parseCoords))
  val sentences = rights(sentencesSource)
  val savedSentences = onlyOnce(sentences.mapAsync(parallelism = 10)(ss => db.saveSentences(ss).map(_ => ss)))
  val drainer = savedSentences.runWith(Sink.ignore)
  val coords = rights(parsedEvents)
  val errors = lefts(parsedEvents)

  errors.runWith(Sink.foreach(err => log.error(s"JSON error for '${err.boat}': '${err.error}'.")))

  def index = Action(Ok(html.map).withCookies(Cookie(Constants.TokenCookieName, mapboxToken.token, httpOnly = false)))

  def boats = WebSocket { rh =>
    authBoat(rh).map { e =>
      e.map { boat =>
        // adds metadata to messages from boats
        val transformer = jsonMessageFlowTransformer.map[BoatEvent, JsValue](
          json => BoatEvent(json, boat),
          out => out
        )
        transformer.transform(Flow.fromSinkAndSource(boatSink, Source.maybe[JsValue]))
      }
    }
  }

  /** Auths with boat token or user/pass. Fails if an invalid token is provided. If no token is provided,
    * tries to auth with user/pass. Fails if invalid user/pass is provided. If no user/pass is provided,
    * falls back to the anonymous user.
    *
    * @param rh request
    * @return
    */
  def authBoat(rh: RequestHeader): Future[Either[Result, BoatInfo]] = {
    rh.headers.get(BoatTokenHeader).map(BoatToken.apply).map { token =>
      auther.authBoat(token).map { e =>
        e.left.map { err =>
          log.error(s"Boat authentication failed. $err")
          Unauthorized(Errors("Unauthorized."))
        }
      }
    }.getOrElse {
      auth(rh).flatMap { e =>
        e.fold(err => Future.successful(Left(err)), user => {
          val boatName = rh.headers.get(BoatNameHeader).map(BoatName.apply).getOrElse(BoatNames.random())
          //        val trackName = rh.headers.get(TrackNameHeader).map(TrackName.apply).getOrElse(TrackNames.random())
          val info = BoatUser(boatName, user)
          db.registerBoat(info).map { boat =>
            Right(BoatInfo(boat.id, boat.name, user))
          }
        })
      }
    }
  }

  def updates = WebSocket.acceptOrResult[JsValue, FrontEvent] { rh =>
    auth(rh).map { outcome =>
      outcome.map { user =>
        val events: Source[FrontEvent, NotUsed] = savedSentences.merge(coords)
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
      auther.authenticate(User(creds.username.name), creds.password).map { outcome =>
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

  /** The publisher-dance makes it so that even with multiple subscribers, `once` only runs once. Without this wrapping,
    * `once` executes independently for each subscriber, which is undesired if `once` involves a side-effect
    * (e.g. a database insert operation).
    *
    * @param once source to only run once for each emitted element
    * @tparam T type of element
    * @tparam U materialized value
    * @return a Source that supports multiple subscribers, but does not independently run `once` for each
    */
  def onlyOnce[T, U](once: Source[T, U]) = Source.fromPublisher(once.runWith(Sink.asPublisher(fanout = true)))
}
