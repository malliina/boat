package controllers

import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.{Done, NotUsed}
import com.malliina.boat.Constants._
import com.malliina.boat._
import com.malliina.boat.auth.EmailAuth
import com.malliina.boat.db._
import com.malliina.boat.html.{BoatHtml, BoatLang}
import com.malliina.boat.http.{AnyBoatRequest, BoatEmailRequest, BoatQuery, BoatRequest, ContentVersions, Limits, TrackQuery, UserRequest}
import com.malliina.boat.parsing.BoatService
import com.malliina.boat.push.BoatState
import com.malliina.values.{Email, Username}
import controllers.BoatController.log
import play.api.Logger
import play.api.data.Form
import play.api.http.{MimeTypes, Writeable}
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
                     googleAuth: EmailAuth,
                     boats: BoatService,
                     db: TracksSource,
                     push: PushDatabase,
                     comps: ControllerComponents)(implicit as: ActorSystem, mat: Materializer)
    extends AuthController(googleAuth, auther, comps)
    with Streams
    with ContentVersions {

  val boatNameForm = Form[BoatName](BoatNames.Key -> BoatNames.mapping)
  val trackTitleForm = Form[TrackTitle](TrackTitle.Key -> TrackTitles.mapping)

  val LanguageSessionKey = "boatLanguage"
  val UserSessionKey = "boatUser"
  val anonUser = Usernames.anon
  implicit val updatesTransformer = jsonMessageFlowTransformer[JsValue, FrontEvent]

  def index = authAction(optionalWebAuth) { req =>
    val maybeBoat = req.user
    val u: Username = maybeBoat.map(_.user).getOrElse(anonUser)
    val lang = maybeBoat.map(_.language).getOrElse(Language.default)
    val tokenCookie = Cookie(TokenCookieName, mapboxToken.token, httpOnly = false)
    val languageCookie = Cookie(LanguageName,
                                maybeBoat.map(_.language).getOrElse(Language.default).code,
                                httpOnly = false)
    val result = Ok(html.map(maybeBoat.getOrElse(UserBoats.anon)))
      .withCookies(tokenCookie, languageCookie)
      .addingToSession(UserSessionKey -> u.name, LanguageSessionKey -> lang.code)(req.req)
    fut(result)
  }

  def tracks = userAction(profile, TrackQuery.apply) { req =>
    db.tracksFor(req.user.email, req.query).map { ts =>
      // Made a mistake in an early API and it's used in early iOS versions.
      // First checks the latest version, then browsers always get the latest since they accept */*.
      def json =
        if (req.rh.accepts(Version2)) Json.toJson(ts)
        else if (req.rh.accepts(Version1))
          Json.toJson(TrackSummaries(ts.tracks.map(t => TrackSummary(t))))
        else Json.toJson(ts)
      respond(req.rh)(
        html = Ok(html.tracks(ts.tracks, req.query, BoatLang(req.user.language).lang)),
        json = Ok(json)
      )
    }
  }

  def track(track: TrackName) = EssentialAction { rh =>
    val action = respond(rh)(
      html = index,
      json = fetchTrack(email => db.ref(track, email))
    )
    action(rh)
  }

  def canonical(track: TrackCanonical) = EssentialAction { rh =>
    val action = respond(rh)(
      html = index,
      json = fetchTrack(email => db.canonical(track, email).map(TrackResponse.apply))
    )
    action(rh)
  }

  def fetchTrack[W: Writes](load: Email => Future[W]) = secureJson(TrackQuery.apply) { req =>
    load(req.user)
  }

  def full(track: TrackName) = secureTrack { req =>
    db.full(track, req.user, req.query).map { track =>
      respond(req.rh)(
        html = Ok(html.list(track, req.query.limits)),
        json = Ok(track)
      )
    }
  }

  def chart(track: TrackName) = secureTrack { req =>
    db.ref(track, req.user).map { ref =>
      Ok(html.chart(ref))
    }
  }

  def modifyTitle(track: TrackName) = trackTitleAction { req =>
    db.modifyTitle(track, req.body, req.user.id)
  }

  def createBoat = boatAction { req =>
    db.addBoat(req.body, req.user.id)
  }

  def renameBoat(id: BoatId) = boatAction { req =>
    db.renameBoat(id, req.body, req.user.id)
  }

  def boatSocket = WebSocket { rh =>
    authBoat(rh).flatMapR { meta =>
      push.push(meta, BoatState.Connected).map(_ => meta)
    }.map { e =>
      e.map { boat =>
        // adds metadata to messages from boats
        val transformer = jsonMessageFlowTransformer.map[BoatEvent, FrontEvent](
          in => BoatEvent(in, boat),
          out => Json.toJson(out)
        )

        val flow: Flow[BoatEvent, PingEvent, NotUsed] = Flow
          .fromSinkAndSource(boats.boatSink, Source.maybe[PingEvent])
          .keepAlive(10.seconds, () => PingEvent(Instant.now.toEpochMilli))
          .backpressureTimeout(3.seconds)
        terminationWatched(transformer.transform(flow)) { _ =>
          log.info(s"Boat '${boat.boatName}' left.")
          push.push(boat, BoatState.Disconnected)
        }
      }
    }
  }

  def clientSocket = WebSocket.acceptOrResult[JsValue, FrontEvent] { rh =>
    recovered(auth(rh), rh).map { outcome =>
      outcome.flatMap { user =>
        BoatQuery(rh).map { limits =>
          val username = user.username
          log.info(s"Viewer '$username' joined.")
          // Show only recent tracks for anon users
          val historicalLimits: BoatQuery =
            if (limits.tracks.nonEmpty && username == anonUser) BoatQuery.tracks(limits.tracks)
            else if (user.username == anonUser) BoatQuery.recent(Instant.now())
            else limits
          val history: Source[CoordsEvent, NotUsed] =
            Source.fromFuture(db.history(user, historicalLimits)).flatMapConcat { es =>
              // unless a sample is specified, return about 300 historical points - this optimization is for charts
              val intelligentSample = math.max(1, es.map(_.coords.length).sum / 300)
              val actualSample = limits.sample.getOrElse(intelligentSample)
              log.debug(
                s"Points ${es.map(_.coords.length).sum} intelligent $intelligentSample actual $actualSample")
              Source(es.toList.map(_.sample(actualSample)))
            }
          val formatter = TimeFormatter(user.language)
          // disconnects viewers that lag more than 3s
          val flow = Flow
            .fromSinkAndSource(
              Sink.ignore,
              history.concat(boats.clientEvents(formatter)).filter(_.isIntendedFor(username)))
            .keepAlive(10.seconds, () => PingEvent(Instant.now.toEpochMilli))
            .backpressureTimeout(3.seconds)
          logTermination(flow, _ => s"Viewer '$username' left.")
        }.left.map { err =>
          BadRequest(Errors(err))
        }
      }
    }
  }

  private def boatAction(
      code: UserRequest[UserInfo, BoatName] => Future[BoatRow]): Action[BoatName] =
    formAction(boatNameForm) { req =>
      code(req).map { b =>
        BoatResponse(b.toBoat)
      }
    }

  private def trackTitleAction(
      code: UserRequest[UserInfo, TrackTitle] => Future[JoinedTrack]): Action[TrackTitle] =
    formAction(trackTitleForm) { req =>
      val formatter = TimeFormatter(req.user.language)
      code(req).map { t =>
        TrackResponse(t.strip(formatter))
      }
    }

  private def formAction[T, W: Writeable](form: Form[T])(
      code: UserRequest[UserInfo, T] => Future[W]): Action[T] =
    parsedAuth(parse.form(form, onErrors = (err: Form[T]) => formError(err)))(profile) { req =>
      code(req).map { w =>
        Ok(w)
      }
    }

  private def logTermination[In, Out, Mat](
      flow: Flow[In, Out, Mat],
      message: Try[Done] => String): Flow[In, Out, Future[Done]] =
    terminationWatched(flow)(t => fut(log.info(message(t))))

  private def secureTrack(run: BoatRequest[TrackQuery, Email] => Future[Result]) =
    secureAction(rh => TrackQuery.withDefault(rh, defaultLimit = 100))(run)

  private def secureJson[T, W: Writes](parse: RequestHeader => Either[SingleError, T])(
      run: BoatRequest[T, Email] => Future[W]) =
    secureAction(parse)(req =>
      run(req).map { w =>
        Ok(Json.toJson(w))
    })

  private def secureAction[T](parse: RequestHeader => Either[SingleError, T])(
      run: BoatRequest[T, Email] => Future[Result]) =
    userAction(authAppOrWeb, parse)(run)

  private def userAction[T, U](authUser: RequestHeader => Future[U],
                               parse: RequestHeader => Either[SingleError, T])(
    run: BoatRequest[T, U] => Future[Result]) =
    authAction(authUser) { req =>
      parse(req.req).fold(
        err => fut(badRequest(err)),
        t => run(AnyBoatRequest(req.user, t, req.req)).recover {
          case nfe: NotFoundException =>
            log.error(nfe.message)
            NotFound(Errors(nfe.message))
        }
      )
    }

  private def formError[T](errors: Form[T]) = {
    log.error(s"Form failure. ${errors.errors}")
    badRequest(SingleError.input("Invalid form input."))
  }

  private def badRequest(error: SingleError) = BadRequest(Errors(error))

  private def terminationWatched[In, Out, Mat](flow: Flow[In, Out, Mat])(
      onTermination: Try[Done] => Future[Unit]): Flow[In, Out, Future[Done]] =
    flow.watchTermination()(Keep.right).mapMaterializedValue { done =>
      done.transformWith { t =>
        onTermination(t).transform { _ =>
          t
        }
      }
    }

  /** Auths with boat token or user/pass. If no credentials are provided, falls back to the anonymous user.
    *
    * Authentication attempts are made in the following order:
    *
    * <ol>
    * <li>Token auth</li>
    * <li>User/pass auth</li>
    * <li>Fallback to anonymous user</li>
    * </ol>
    *
    * Authentication fails if any provided credentials are invalid. The next step is only taken if no credentials are
    * provided.
    *
    * @param rh request
    * @return
    */
  private def authBoat(rh: RequestHeader): Future[Either[Result, TrackMeta]] =
    recovered(boatAuth(rh).flatMap(meta => db.join(meta)), rh)

  private def boatAuth(rh: RequestHeader): Future[BoatTrackMeta] =
    rh.headers
      .get(BoatTokenHeader)
      .map(BoatToken.apply)
      .map { token =>
        auther.authBoat(token).map { info =>
          BoatUser(trackOrRandom(rh), info.boatName, info.username)
        }
      }
      .getOrElse {
        val boatName =
          rh.headers.get(BoatNameHeader).map(BoatName.apply).getOrElse(BoatNames.random())
        fut(BoatUser(trackOrRandom(rh), boatName, anonUser))
      }

  private def auth(rh: RequestHeader): Future[MinimalUserInfo] =
    authApp(rh).recover {
      case _: MissingCredentialsException =>
        authSessionUser(rh).getOrElse(MinimalUserInfo.anon)
    }

  private def authSessionUser(rh: RequestHeader): Option[MinimalUserInfo] =
    rh.session.get(UserSessionKey).filter(_ != Usernames.anon.name).map { user =>
      SimpleUserInfo(
        Username(user),
        rh.session.get(LanguageSessionKey).map(Language.apply).getOrElse(Language.default)
      )
    }

  private def authApp(rh: RequestHeader): Future[MinimalUserInfo] =
    googleProfile(rh)

  /** Optional authentication for the web.
    *
    * <p>If the user has a session, read it and return authenticated user info.
    * <p>If the user has no session, but a Google auth cookie, redirect to the social login to start the OAuth flow.
    * <p>If the user has no session and no auth cookie, return None which means unauthenticated mode.
    */
  private def optionalWebAuth(rh: RequestHeader): Future[Option[UserBoats]] =
    sessionEmail(rh).map { email =>
      auther.boats(email).map { boats =>
        Option(boats)
      }
    }.getOrElse {
      googleCookie(rh).map { _ =>
        Future.failed(IdentityException.missingCredentials(rh))
      }.getOrElse {
        fut(None)
      }
    }

  private def trackOrRandom(rh: RequestHeader): TrackName = TrackNames.random()

  private def respond[A](rh: RequestHeader)(html: => A, json: => A): A =
    if (rh.accepts(MimeTypes.HTML) && rh.getQueryString("json").isEmpty) html
    else json
}
