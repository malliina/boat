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
import com.malliina.concurrent.Execution.cached
import com.malliina.values.{Email, Username}
import controllers.Assets.Asset
import controllers.BoatController.log
import controllers.Social.{EmailKey, GoogleCookie, ProviderCookieName}
import play.api.Logger
import play.api.data.Form
import play.api.http.Writeable
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
                     comps: ControllerComponents,
                     assets: AssetsBuilder)(implicit as: ActorSystem, mat: Materializer)
  extends AbstractController(comps) with Streams {

  val boatNameForm = Form[BoatName]("boatName" -> BoatNames.mapping)

  val UserSessionKey = "user"
  val anonUser = Usernames.anon
  implicit val updatesTransformer = jsonMessageFlowTransformer[JsValue, FrontEvent]

  implicit def writeable[T: Writes] = Writeable.writeableOf_JsValue.map[T](t => Json.toJson(t))

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
    val user = req.user
    val u = user.map(_.user).getOrElse(anonUser)
    val cookie = Cookie(TokenCookieName, mapboxToken.token, httpOnly = false)
    val result = Ok(html.map(user))
      .withCookies(cookie)
      .addingToSession(UserSessionKey -> u.name)(req.req)
    fut(result)
  }

  def health = Action {
    Ok(Json.toJson(AppMeta.default))
  }

  def me = authAction(profile) { req =>
    fut(Ok(UserContainer(req.user)))
  }

  def enableNotifications = parsedAuth(parse.json[PushPayload])(profile) { req =>
    val payload = req.body
    push.enable(PushInput(payload.token, payload.device, req.user.id)).map { _ =>
      Ok(SimpleMessage("enabled"))
    }
  }

  def disableNotifications = parsedAuth(parse.json[SingleToken])(profile) { req =>
    push.disable(req.body.token, req.user.id).map { disabled =>
      val msg = if (disabled) "disabled" else "no change"
      Ok(SimpleMessage(msg))
    }
  }

  def createBoat = boatAction(req => db.addBoat(req.body, req.user.id))

  def renameBoat(id: BoatId) = boatAction(req => db.renameBoat(id, req.user.id, req.body))

  private def boatAction(code: BoatRequest[UserInfo, BoatName] => Future[BoatRow]) =
    parsedAuth(parse.form(boatNameForm, onErrors = (err: Form[BoatName]) => formError(err)))(authAppToken) { req =>
      code(req).map { boat => Ok(Json.obj("boat" -> boat.toBoat)) }
    }

  def pingAuth = authAction(authAppToken) { _ => Future.successful(Ok(Json.toJson(AppMeta.default))) }

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

  def tracks = secureJson(TrackQuery.apply) { req =>
    db.tracksFor(req.email, req.query)
  }

  def summary(track: TrackName) = secureJson(TrackQuery.apply) { _ =>
    db.summary(track)
  }

  def distances = secureJson(_ => Right(())) { req =>
    db.distances(req.email)
  }

  def track(track: TrackName) = secureJson(TrackQuery.apply) { req =>
    db.track(track, req.email, req.query)
  }

  def full(track: TrackName) = secureJson(TrackQuery.apply) { req =>
    db.full(track, req.email, req.query)
  }

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

  def updates = WebSocket.acceptOrResult[JsValue, FrontEvent] { rh =>
    makeResult(auth(rh), rh).map { outcome =>
      outcome.flatMap { user =>
        BoatQuery(rh).map { limits =>
          log.info(s"Viewer '$user' joined.")
          // Show recent tracks for non-anon users
          val historicalLimits: BoatQuery =
            if (limits.tracks.nonEmpty && user == anonUser) BoatQuery.tracks(limits.tracks)
            else if (user == anonUser) BoatQuery.recent(Instant.now())
            else limits
          val history: Source[CoordsEvent, NotUsed] =
            Source.fromFuture(db.history(user, historicalLimits)).flatMapConcat(es => Source(es.toList.map(_.sample(4))))
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

  def terminationWatched[In, Out, Mat](flow: Flow[In, Out, Mat])(onTermination: Try[Done] => Future[Unit]): Flow[In, Out, Future[Done]] =
    flow.watchTermination()(Keep.right).mapMaterializedValue { done =>
      done.transformWith { t =>
        onTermination(t).transform { _ => t }
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
  private def authBoat(rh: RequestHeader): Future[Either[Result, TrackMeta]] =
    makeResult(boatAuth(rh), rh).flatMap { e =>
      e.fold(
        err => fut(Left(err)),
        boat => db.join(boat).map(Right.apply)
      )
    }

  private def boatAuth(rh: RequestHeader): Future[Either[IdentityError, BoatTrackMeta]] =
    rh.headers.get(BoatTokenHeader).map(BoatToken.apply).map { token =>
      auther.authBoat(token).map { e =>
        e.map { info =>
          BoatUser(trackOrRandom(rh), info.boatName, info.username)
        }
      }
    }.getOrElse {
      val boatName = rh.headers.get(BoatNameHeader).map(BoatName.apply).getOrElse(BoatNames.random())
      fut(Right(BoatUser(trackOrRandom(rh), boatName, anonUser)))
    }

  private def auth(rh: RequestHeader): Future[Either[IdentityError, Username]] =
    authApp(rh)
      .orElse(authSessionUser(rh))
      .getOrElse(fut(Right(anonUser)))

  private def authSessionUser(rh: RequestHeader) =
    rh.session.get(UserSessionKey).filter(_ != Usernames.anon.name).map { user =>
      fut(Right(Username(user)))
    }

  private def authAppToken(rh: RequestHeader): Future[Either[IdentityError, UserInfo]] =
    googleProfile(rh).getOrElse(Future.successful(Left(MissingCredentials(rh))))

  private def authApp(rh: RequestHeader): Option[Future[Either[IdentityError, Username]]] =
    googleProfile(rh).map { f =>
      f.mapR(_.username)
    }

  private def googleProfile(rh: RequestHeader): Option[Future[Either[IdentityError, UserInfo]]] =
    googleAuth.auth(rh).map { f =>
      f.flatMapRight { email =>
        auther.authEmail(email)
      }
    }

  private def profile(rh: RequestHeader): Future[Either[IdentityError, UserInfo]] =
    authAppOrWeb(rh).flatMapRight { email =>
      auther.authEmail(email)
    }

  private def authAppOrWeb(rh: RequestHeader): Future[Either[IdentityError, Email]] = {
    def authFromSession = fut(sessionEmail(rh).toRight(MissingCredentials(rh)))

    googleAuth.auth(rh).getOrElse(authFromSession)
  }

  def queryAuthBoat(rh: RequestHeader, onNoQuery: => Either[IdentityError, Option[JoinedBoat]]): Future[Either[IdentityError, Option[JoinedBoat]]] =
    rh.getQueryString(BoatTokenQuery).map { token =>
      auther.authBoat(BoatToken(token)).map(_.map(Option.apply))
    }.getOrElse {
      fut(onNoQuery)
    }

  def optionalAuth(rh: RequestHeader): Future[Either[IdentityError, Option[BoatInfo]]] =
    authSessionEmail(rh).map(opt => Right(opt))

  def authSessionEmail(rh: RequestHeader): Future[Option[BoatInfo]] =
    sessionEmail(rh).map { email =>
      auther.boats(email).map { boats =>
        boats.headOption
      }
    }.getOrElse {
      fut(None)
    }

  private def sessionEmail(rh: RequestHeader): Option[Email] =
    rh.session.get(EmailKey).map(Email.apply)

  private def authAction[U](authenticate: RequestHeader => Future[Either[IdentityError, U]])(code: BoatRequest[U, AnyContent] => Future[Result]) =
    parsedAuth(parse.default)(authenticate)(code)

  private def parsedAuth[U, B](p: BodyParser[B])(authenticate: RequestHeader => Future[Either[IdentityError, U]])(code: BoatRequest[U, B] => Future[Result]) =
    Action(p).async { req =>
      makeResult(authenticate(req), req).flatMap { e =>
        e.fold(fut, t => code(BoatRequest(t, req)))
      }
    }

  private def makeResult[T](f: Future[Either[IdentityError, T]], rh: RequestHeader): Future[Either[Result, T]] =
    f.map { e =>
      e.left.map {
        case MissingCredentials(req) =>
          checkLoginCookie(req).getOrElse(unauth)
        case err =>
          log.warn(s"Authentication failed from '$rh': '$err'.")
          unauth
      }
    }

  private def checkLoginCookie(rh: RequestHeader): Option[Result] =
    rh.cookies.get(ProviderCookieName).filter(_.value == GoogleCookie).map { _ =>
      log.info(s"Redir to login")
      Redirect(routes.Social.google())
    }

  private def unauth = Unauthorized(Errors(SingleError("Unauthorized.")))

  private def trackOrRandom(rh: RequestHeader): TrackName = TrackNames.random()

  private def saveRecovered(coord: FullCoord): Future[List[CoordsEvent]] =
    db.saveCoords(coord)
      .map { inserted => List(CoordsEvent(Seq(coord.timed(inserted.point)), inserted.track)) }
      .recover { case t =>
        log.error(s"Unable to save coords.", t)
        Nil
      }

  def fut[T](t: T): Future[T] = Future.successful(t)
}
