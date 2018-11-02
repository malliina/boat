package controllers

import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, Sink, Source}
import akka.{Done, NotUsed}
import com.malliina.boat.Constants._
import com.malliina.boat._
import com.malliina.boat.auth.GoogleTokenAuth
import com.malliina.boat.db._
import com.malliina.boat.html.BoatHtml
import com.malliina.boat.http.{BoatEmailRequest, BoatQuery, BoatRequest, TrackQuery}
import com.malliina.boat.parsing.{BoatParser, FullCoord, ParsedSentence}
import com.malliina.boat.push.BoatState
import com.malliina.values.Username
import controllers.BoatController.log
import play.api.Logger
import play.api.data.Form
import play.api.http.MimeTypes
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.WebSocket.MessageFlowTransformer.jsonMessageFlowTransformer
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Try

object BoatController {
  private val log = Logger(getClass)
}

class BoatController(mapboxToken: AccessToken,
                     html: BoatHtml,
                     auther: UserManager,
                     googleAuth: GoogleTokenAuth,
                     db: TracksSource,
                     push: PushDatabase,
                     comps: ControllerComponents)(implicit as: ActorSystem, mat: Materializer)
  extends AuthController(googleAuth, auther, comps)
    with Streams {

  val boatNameForm = Form[BoatName](BoatNames.Key -> BoatNames.mapping)

  val UserSessionKey = "user"
  val anonUser = Usernames.anon
  implicit val updatesTransformer = jsonMessageFlowTransformer[JsValue, FrontEvent]

  // Publish-Subscribe Akka Streams
  // https://doc.akka.io/docs/akka/2.5/stream/stream-dynamic.html
  val (boatSink, viewerSource) = MergeHub.source[BoatEvent](perProducerBufferSize = 16)
    .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
    .run()
  val _ = viewerSource.runWith(Sink.ignore)
  val sentencesSource = viewerSource.map { boatEvent =>
    BoatParser.read[SentencesMessage](boatEvent.message)
      .map(_.toEvent(boatEvent.from.short))
      .left.map(err => BoatJsonError(err, boatEvent))
  }
  val sentences = rights(sentencesSource)
  val savedSentences = monitored(onlyOnce(sentences.mapAsync(parallelism = 1)(ss => db.saveSentences(ss))), "saved sentences")
  val sentencesDrainer = savedSentences.runWith(Sink.ignore)

  val parsedSentences = monitored(savedSentences.mapConcat[ParsedSentence](e => BoatParser.parseMulti(e).toList), "parsed sentences")
  parsedSentences.runWith(Sink.ignore)
  val parsedEvents: Source[FullCoord, Future[Done]] = parsedSentences.via(BoatParser.multiFlow())
  val savedCoords = monitored(onlyOnce(parsedEvents.mapAsync(parallelism = 1)(ce => saveRecovered(ce))), "saved coords")
  val coordsDrainer = savedCoords.runWith(Sink.ignore)
  val errors = lefts(sentencesSource)
  val frontEvents: Source[CoordsEvent, Future[Done]] = savedCoords.mapConcat[CoordsEvent](identity)

  errors.runWith(Sink.foreach(err => log.error(s"JSON error for '${err.boat}': '${err.error}'.")))

  def index = authAction(optionalAuth) { req =>
    val maybeBoat = req.user
    val u: Username = maybeBoat.map(_.user).getOrElse(anonUser)
    val cookie = Cookie(TokenCookieName, mapboxToken.token, httpOnly = false)
    val result = Ok(html.map(maybeBoat))
      .withCookies(cookie)
      .addingToSession(UserSessionKey -> u.name)(req.req)
    fut(result)
  }

  def tracks = secureJson(TrackQuery.apply) { req =>
    db.tracksFor(req.email, req.query)
  }

  def distances = secureJson(_ => Right(())) { req =>
    db.distances(req.email)
  }

  def track(track: TrackName) = EssentialAction { rh =>
    val action = respond(rh)(
      html = index,
      json = summary(track)
    )
    action(rh)
  }

  def summary(track: TrackName) = secureJson(TrackQuery.apply) { _ =>
    db.summary(track)
  }

  def trail(track: TrackName) = secureJson(TrackQuery.apply) { req =>
    db.track(track, req.email, req.query)
  }

  def full(track: TrackName) = secureTrack { req =>
    db.full(track, req.email, req.query).map { track =>
      respond(req.rh)(
        html = Ok(html.list(track, req.query.limits)),
        json = Ok(track)
      )
    }
  }

  def chart(track: TrackName) = secureTrack { req =>
    db.ref(track).map { ref =>
      Ok(html.chart(ref))
    }
  }

  def createBoat = boatAction(req => db.addBoat(req.body, req.user.id))

  def renameBoat(id: BoatId) = boatAction(req => db.renameBoat(id, req.user.id, req.body))

  private def boatAction(code: BoatRequest[UserInfo, BoatName] => Future[BoatRow]): Action[BoatName] =
    parsedAuth(parse.form(boatNameForm, onErrors = (err: Form[BoatName]) => formError(err)))(googleProfile) { req =>
      code(req).map { boat => Ok(BoatResponse(boat.toBoat)) }
    }

  def boats = WebSocket { rh =>
    authBoat(rh).flatMapR { meta =>
      push.push(meta, BoatState.Connected).map(_ => meta)
    }.map { e =>
      e.map { boat =>
        // adds metadata to messages from boats
        val transformer = jsonMessageFlowTransformer.map[BoatEvent, FrontEvent](
          in => BoatEvent(in, boat),
          out => Json.toJson(out)
        )

        val flow: Flow[BoatEvent, PingEvent, NotUsed] = Flow.fromSinkAndSource(boatSink, Source.maybe[PingEvent])
          .keepAlive(10.seconds, () => PingEvent(Instant.now.toEpochMilli))
          .backpressureTimeout(3.seconds)
        terminationWatched(transformer.transform(flow)) { _ =>
          log.info(s"Boat '${boat.boatName}' left.")
          push.push(boat, BoatState.Disconnected)
        }
      }
    }
  }

  def updates = WebSocket.acceptOrResult[JsValue, FrontEvent] { rh =>
    recovered(auth(rh), rh).map { outcome =>
      outcome.flatMap { user =>
        BoatQuery(rh).map { limits =>
          log.info(s"Viewer '$user' joined.")
          // Show recent tracks for non-anon users
          val historicalLimits: BoatQuery =
            if (limits.tracks.nonEmpty && user == anonUser) BoatQuery.tracks(limits.tracks)
            else if (user == anonUser) BoatQuery.recent(Instant.now())
            else limits
          val history: Source[CoordsEvent, NotUsed] =
            Source.fromFuture(db.history(user, historicalLimits)).flatMapConcat { es =>
              // unless a sample is specified, return about 300 historical points - this optimization is for charts
              val intelligentSample = math.max(1, es.map(_.coords.length).sum / 300)
              val actualSample = limits.sample.getOrElse(intelligentSample)
              log.debug(s"Points ${es.map(_.coords.length).sum} intelligent $intelligentSample actual $actualSample")
              Source(es.toList.map(_.sample(actualSample)))
            }
          // disconnects viewers that lag more than 3s
          val flow = Flow.fromSinkAndSource(Sink.ignore, history.concat(frontEvents).filter(_.isIntendedFor(user)))
            .keepAlive(10.seconds, () => PingEvent(Instant.now.toEpochMilli))
            .backpressureTimeout(3.seconds)
          logTermination(flow, _ => s"Viewer '$user' left.")
        }.left.map { err =>
          BadRequest(Errors(err))
        }
      }
    }
  }

  def logTermination[In, Out, Mat](flow: Flow[In, Out, Mat], message: Try[Done] => String): Flow[In, Out, Future[Done]] =
    terminationWatched(flow)(t => fut(log.info(message(t))))

  def monitored[In, Mat](src: Source[In, Mat], label: String): Source[In, Future[Done]] =
    src.watchTermination()(Keep.right).mapMaterializedValue { done =>
      done.transform { tryDone =>
        tryDone.fold(
          t => log.error(s"Error in flow '$label'.", t),
          _ => log.warn(s"Flow '$label' completed.")
        )
        tryDone
      }
    }

  private def secureTrack(run: BoatEmailRequest[TrackQuery] => Future[Result]) =
    secureAction(rh => TrackQuery.withDefault(rh, defaultLimit = 100))(run)

  private def secureJson[T, W: Writes](parse: RequestHeader => Either[SingleError, T])(run: BoatEmailRequest[T] => Future[W]) =
    secureAction(parse)(req => run(req).map { w => Ok(Json.toJson(w)) })

  private def secureAction[T](parse: RequestHeader => Either[SingleError, T])(run: BoatEmailRequest[T] => Future[Result]) =
    authAction(authAppOrWeb) { req =>
      parse(req.req).fold(
        err => fut(BadRequest(Errors(err))),
        t => run(BoatEmailRequest(t, req.user, req.req))
      )
    }

  def formError[T](errors: Form[T]) = {
    log.error(s"Form failure. ${errors.errors}")
    badRequest(SingleError("Invalid input."))
  }

  def badRequest(error: SingleError) = BadRequest(Errors(error))

  def terminationWatched[In, Out, Mat](flow: Flow[In, Out, Mat])(onTermination: Try[Done] => Future[Unit]): Flow[In, Out, Future[Done]] =
    flow.watchTermination()(Keep.right).mapMaterializedValue { done =>
      done.transformWith { t =>
        onTermination(t).transform { _ => t }
      }
    }

  /** Auths with boat token or user/pass. Fails if an invalid token is provided. If no token is provided,
    * tries to auth with user/pass. Fails if invalid user/pass is provided. If no user/pass is provided,
    * falls back to the anonymous user.
    *
    * @param rh request
    * @return
    */
  private def authBoat(rh: RequestHeader): Future[Either[Result, TrackMeta]] =
    recovered(boatAuth(rh).flatMap(meta => db.join(meta)), rh)

  private def boatAuth(rh: RequestHeader): Future[BoatTrackMeta] =
    rh.headers.get(BoatTokenHeader).map(BoatToken.apply).map { token =>
      auther.authBoat(token).map { info =>
        BoatUser(trackOrRandom(rh), info.boatName, info.username)
      }
    }.getOrElse {
      val boatName = rh.headers.get(BoatNameHeader).map(BoatName.apply).getOrElse(BoatNames.random())
      fut(BoatUser(trackOrRandom(rh), boatName, anonUser))
    }

  private def auth(rh: RequestHeader): Future[Username] =
    authApp(rh).recover {
      case _: MissingCredentialsException =>
        authSessionUser(rh).getOrElse(anonUser)
    }

  private def authSessionUser(rh: RequestHeader): Option[Username] =
    rh.session.get(UserSessionKey).filter(_ != Usernames.anon.name).map { user =>
      Username(user)
    }

  private def authApp(rh: RequestHeader): Future[Username] =
    googleProfile(rh).map(_.username)

  private def optionalAuth(rh: RequestHeader): Future[Option[BoatInfo]] =
    sessionEmail(rh).map { email =>
      auther.boats(email).map { boats =>
        boats.headOption
      }
    }.getOrElse {
      fut(None)
    }

  private def trackOrRandom(rh: RequestHeader): TrackName = TrackNames.random()

  private def saveRecovered(coord: FullCoord): Future[List[CoordsEvent]] =
    db.saveCoords(coord)
      .map { inserted => List(CoordsEvent(Seq(coord.timed(inserted.point)), inserted.track)) }
      .recover { case t =>
        log.error(s"Unable to save coords.", t)
        Nil
      }

  private def respond[A](rh: RequestHeader)(html: => A, json: => A): A =
    if (rh.accepts(MimeTypes.HTML) && rh.getQueryString("json").isEmpty) html
    else json
}
