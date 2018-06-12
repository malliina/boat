package controllers

import java.time.Instant

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, Sink, Source}
import com.malliina.boat.Constants.{BoatNameHeader, BoatTokenHeader, TokenCookieName, TrackNameHeader}
import com.malliina.boat._
import com.malliina.boat.db.{IdentityError, TracksSource, UserManager}
import com.malliina.boat.http.BoatQuery
import com.malliina.boat.parsing.BoatParser.{parseCoords, read}
import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.play.auth.Auth
import controllers.Assets.Asset
import controllers.BoatController.{anonUser, log}
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.WebSocket.MessageFlowTransformer.jsonMessageFlowTransformer
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object BoatController {
  private val log = Logger(getClass)

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
  val sentencesDrainer = savedSentences.runWith(Sink.ignore)
  val coords: Source[CoordsEvent, NotUsed] = rights(parsedEvents)
  val savedCoords = onlyOnce(coords.mapAsync(parallelism = 10)(coordsEvent => db.saveCoords(coordsEvent).map(_ => coordsEvent)))
  val coordsDrainer = savedCoords.runWith(Sink.ignore)
  val errors = lefts(parsedEvents)
  val frontEvents: Source[FrontEvent, NotUsed] = savedSentences.merge(savedCoords)

  errors.runWith(Sink.foreach(err => log.error(s"JSON error for '${err.boat}': '${err.error}'.")))

  def index = Action {
    val cookie = Cookie(TokenCookieName, mapboxToken.token, httpOnly = false)
    Ok(html.map).withCookies(cookie)
  }

  def health = Action {
    Ok(Json.toJson(AppMeta.default))
  }

  def boats = WebSocket { rh =>
    authBoat(rh).map { e =>
      e.map { boat =>
        // adds metadata to messages from boats
        val transformer = jsonMessageFlowTransformer.map[BoatEvent, FrontEvent](
          json => BoatEvent(json, boat),
          out => Json.toJson(out)
        )
        val flow: Flow[BoatEvent, PingEvent, NotUsed] = Flow.fromSinkAndSource(boatSink, Source.maybe[PingEvent])
          .keepAlive(10.seconds, () => PingEvent(Instant.now.toEpochMilli))
          .backpressureTimeout(3.seconds)
        transformer.transform(flow)
      }
    }
  }

  def updates = WebSocket.acceptOrResult[JsValue, FrontEvent] { rh =>
    asResult(auth(rh), rh).map { outcome =>
      outcome.flatMap { user =>
        BoatQuery(rh).map { limits =>
          // Show recent tracks for non-anon users
          val historicalLimits: BoatQuery =
            if (user == anonUser) BoatQuery.recent(Instant.now())
            else limits
          val history: Source[CoordsEvent, NotUsed] =
            Source.fromFuture(db.history(user, historicalLimits)).flatMapConcat(es => Source(es.toList))
          // disconnects viewers that lag more than 3s
          Flow.fromSinkAndSource(Sink.ignore, history.concat(frontEvents).filter(_.isIntendedFor(user)))
            .keepAlive(10.seconds, () => PingEvent(Instant.now.toEpochMilli))
            .backpressureTimeout(3.seconds)
        }.left.map { err =>
          BadRequest(Errors(err.message))
        }
      }
    }
  }

  def versioned(path: String, file: Asset): Action[AnyContent] =
    assets.versioned(path, file)

  /** Auths with boat token or user/pass. Fails if an invalid token is provided. If no token is provided,
    * tries to auth with user/pass. Fails if invalid user/pass is provided. If no user/pass is provided,
    * falls back to the anonymous user.
    *
    * @param rh request
    * @return
    */
  def authBoat(rh: RequestHeader): Future[Either[Result, JoinedTrack]] =
    asResult(boatAuth(rh), rh).flatMap { e =>
      e.fold(
        err => Future.successful(Left(err)),
        boat => db.join(boat).map(Right.apply)
      )
    }

  def boatAuth(rh: RequestHeader): Future[Either[IdentityError, BoatMeta]] =
    rh.headers.get(BoatTokenHeader).map(BoatToken.apply).map { token =>
      auther.authBoat(token).map { e =>
        e.map { info =>
          BoatUser(trackOrRandom(rh), info.boat, info.user)
        }
      }
    }.getOrElse {
      auth(rh).map { e =>
        e.map { user =>
          val boatName = rh.headers.get(BoatNameHeader).map(BoatName.apply).getOrElse(BoatNames.random())
          BoatUser(trackOrRandom(rh), boatName, user)
        }
      }
    }

  def auth(rh: RequestHeader): Future[Either[IdentityError, User]] =
    Auth.basicCredentials(rh).map { creds =>
      auther.authenticate(User(creds.username.name), creds.password).map { outcome =>
        outcome.map { profile =>
          profile.username
        }
      }
    }.getOrElse {
      Future.successful(Right(anonUser))
    }

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

  private def asResult[T](f: Future[Either[IdentityError, T]], rh: RequestHeader) =
    f.map { e =>
      e.left.map { err =>
        log.warn(s"Authentication failed from '$rh': '$err'.")
        Unauthorized(Errors("Unauthorized."))
      }
    }

  private def trackOrRandom(rh: RequestHeader) =
    rh.headers.get(TrackNameHeader).map(TrackName.apply).getOrElse(TrackNames.random())
}
