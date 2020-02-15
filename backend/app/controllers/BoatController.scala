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
import com.malliina.boat.http._
import com.malliina.boat.parsing.{BoatService, DeviceService}
import com.malliina.boat.push.{BoatState, PushService}
import com.malliina.values.Username
import controllers.BoatController.log
import play.api.Logger
import play.api.data.{Form, Forms}
import play.api.http.{MimeTypes, Writeable}
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.WebSocket.MessageFlowTransformer.jsonMessageFlowTransformer
import play.api.mvc._
import play.filters.csrf.CSRF

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

object BoatController {
  private val log = Logger(getClass)
}

class BoatController(
  mapboxToken: AccessToken,
  html: BoatHtml,
  auther: UserManager,
  googleAuth: EmailAuth,
  boatService: BoatService,
  deviceService: DeviceService,
  db: TracksSource,
  push: PushService,
  comps: ControllerComponents
)(implicit as: ActorSystem, mat: Materializer)
  extends AuthController(googleAuth, auther, comps)
  with Streams
  with ContentVersions {
  val reverse = routes.BoatController

  val boatNameForm = Form[BoatName](BoatNames.Key -> BoatNames.mapping)
  val trackTitleForm = Form[TrackTitle](TrackTitle.Key -> TrackTitles.mapping)
  val trackCommentsForm = Form[String](TrackComments.Key -> Forms.nonEmptyText)

  val LanguageSessionKey = "boatLanguage"
  val UserSessionKey = "boatUser"
  val anonUser = Usernames.anon
  implicit val updatesTransformer =
    jsonMessageFlowTransformer[JsValue, FrontEvent]

  def index = authAction(optionalWebAuth) { req =>
    val maybeBoat = req.user
    val u: Username = maybeBoat.map(_.user).getOrElse(anonUser)
    val lang = maybeBoat.map(_.language).getOrElse(Language.default)
    val tokenCookie =
      Cookie(TokenCookieName, mapboxToken.token, httpOnly = false)
    val languageCookie = Cookie(
      LanguageName,
      maybeBoat.map(_.language).getOrElse(Language.default).code,
      httpOnly = false
    )
    val result = Ok(html.map(maybeBoat.getOrElse(UserBoats.anon)))
      .withCookies(tokenCookie, languageCookie)
      .addingToSession(
        UserSessionKey -> u.name,
        LanguageSessionKey -> lang.code
      )(req.req)
    fut(result)
  }

  def devices = userAction(profile, _ => Right(())) { req =>
    Future.successful {
      CSRF
        .getToken(req.rh)
        .map { token =>
          Ok(html.devices(req.user, token))
        }
        .getOrElse {
          InternalServerError
        }
    }
  }

  def tracks = negotiated(tracksHtml, tracksJson)

  def history = userAction(profile, BoatQuery.apply) { req =>
    db.history(req.user, req.query).map { e =>
      Ok(Json.toJson(e))
    }
  }

  private def tracksJson = userAction(profile, TrackQuery.apply) { req =>
    db.tracksFor(req.user, req.query).map { ts =>
      // Made a mistake in an early API and it's used in early iOS versions.
      // First checks the latest version, then browsers always get the latest since they accept */*.
      def json =
        if (req.rh.accepts(Version2))
          Json.toJson(ts)
        else if (req.rh.accepts(Version1))
          Json.toJson(TrackSummaries(ts.tracks.map(t => TrackSummary(t))))
        else
          Json.toJson(ts)
      Ok(json)
    }
  }

  private def tracksHtml = userAction(profile, TrackQuery.apply) { req =>
    val lang = BoatLang(req.user.language).lang
    db.tracksBundle(req.user, req.query, lang).map { ts =>
      Ok(html.tracks(ts, req.query, lang))
    }
  }

  def track(track: TrackName) = negotiated(
    html = index,
    json = fetchTrack(user => db.ref(track, user.language))
  )

  def canonical(track: TrackCanonical) = negotiated(
    html = index,
    json = fetchTrack(user => db.canonical(track, user.language).map(TrackResponse.apply))
  )

  private def negotiated(html: Action[AnyContent], json: Action[AnyContent]) =
    EssentialAction { rh =>
      respond(rh)(html, json)(rh)
    }

  def fetchTrack[W: Writes](load: MinimalUserInfo => Future[W]) =
    secureJson(TrackQuery.apply) { req =>
      load(req.user)
    }

  def full(track: TrackName) = secureTrack { req =>
    val lang = req.user.language
    db.full(track, lang, req.query).map { track =>
      respond(req.rh)(
        html = Ok(html.list(track, req.query.limits, BoatLang(lang))),
        json = Ok(track)
      )
    }
  }

  def chart(track: TrackName) = secureTrack { req =>
    val lang = req.user.language
    db.ref(track, lang).map { ref =>
      Ok(html.chart(ref, BoatLang(lang)))
    }
  }

  def modifyTitle(track: TrackName) = trackAction(trackTitleForm) { req =>
    db.updateTitle(track, req.body, req.user.id)
  }

  def updateComments(track: TrackId) = trackAction(trackCommentsForm) { req =>
    db.updateComments(track, req.body, req.user.id)
  }

  def createBoat = formActionResult(boatNameForm) { req =>
    db.addBoat(req.body, req.user.id).map { boat =>
      respond(req.req)(
        Redirect(reverse.devices()),
        Ok(BoatResponse(boat.toBoat))
      )
    }
  }

  def deleteBoat(id: DeviceId) = authAction(profile) { req =>
    db.removeDevice(id, req.user.id).map { rows =>
      respond(req.req)(Redirect(reverse.devices()), Ok(SimpleMessage("Done.")))
    }
  }

  def renameBoat(id: DeviceId) = boatAction { req =>
    db.renameBoat(id, req.body, req.user.id)
  }

  def stats = userAction(profile, TrackQuery.apply) { req =>
    db.stats(req.user, req.query, BoatLang(req.user.language).lang).map { sr =>
      Ok(Json.toJson(sr))
    }
  }

  def deviceSocket = WebSocket { rh =>
    authDevice(rh).flatMapR { meta =>
      log.info(s"Device '${meta.boatName}' joined.")
      push.push(meta, BoatState.Connected).map(_ => meta).recover {
        case e =>
          log.error("Failed to push all device notifications.", e)
          meta
      }
    }.map { e =>
      e.map { meta =>
        val transformer =
          jsonMessageFlowTransformer.map[DeviceEvent, FrontEvent](
            in => DeviceEvent(in, meta),
            out => Json.toJson(out)
          )
        val flow: Flow[DeviceEvent, PingEvent, NotUsed] =
          Flow
            .fromSinkAndSource(
              deviceService.deviceSink,
              Source.maybe[PingEvent]
            )
            .keepAlive(10.seconds, () => PingEvent(Instant.now.toEpochMilli))
            .backpressureTimeout(3.seconds)
        terminationWatched(transformer.transform(flow)) { tryDone =>
          tryDone match {
            case Success(_) =>
              log.info(s"Device '${meta.boatName}' left.")
            case Failure(f) =>
              log.error(s"Device '${meta.boatName}' left.", f)
          }
          Future.successful(())
        }
      }
    }.recoverWith {
      case t =>
        log.error("Failed to authenticate device.", t)
        Future.failed(t)
    }
  }

  def boatSocket = WebSocket { rh =>
    authBoat(rh).flatMapR { meta =>
      log.info(s"Boat '${meta.boatName}' joined.")
      push.push(meta, BoatState.Connected).map(_ => meta).recover {
        case e =>
          log.error("Failed to push all boat notifications.", e)
          meta
      }
    }.map { e =>
      e.map { boat =>
        // adds metadata to messages from boats
        val transformer =
          jsonMessageFlowTransformer.map[BoatEvent, FrontEvent](
            in => BoatEvent(in, boat),
            out => Json.toJson(out)
          )
        val flow: Flow[BoatEvent, PingEvent, NotUsed] = Flow
          .fromSinkAndSource(boatService.boatSink, Source.maybe[PingEvent])
          .keepAlive(10.seconds, () => PingEvent(Instant.now.toEpochMilli))
          .backpressureTimeout(3.seconds)
        terminationWatched(transformer.transform(flow)) { tryDone =>
          tryDone match {
            case Success(_) =>
              log.info(s"Boat '${boat.boatName}' left.")
            case Failure(f) =>
              log.error(s"Boat '${boat.boatName}' left.", f)
          }
          push.push(boat, BoatState.Disconnected)
        }
      }
    }.recoverWith {
      case t =>
        log.error("Failed to authenticate boat.", t)
        Future.failed(t)
    }
  }

  def clientSocket = WebSocket.acceptOrResult[JsValue, FrontEvent] { rh =>
    recovered(authOrAnon(rh), rh).map { outcome =>
      outcome.flatMap { user =>
        BoatQuery(rh).map { limits =>
          val username = user.username
          log.info(s"Viewer '$username' joined.")
          val historicalLimits: BoatQuery =
            if (limits.tracks.nonEmpty && username == anonUser)
              BoatQuery.tracks(limits.tracks)
            else if (username == anonUser) BoatQuery.empty
            else limits
          val history: Source[CoordsEvent, NotUsed] =
            Source
              .future(db.history(user, historicalLimits))
              .flatMapConcat { es =>
                // unless a sample is specified, return about 300 historical points - this optimization is for charts
                val intelligentSample =
                  math.max(1, es.map(_.coords.length).sum / 300)
                val actualSample = limits.sample.getOrElse(intelligentSample)
                log.debug(
                  s"Points ${es.map(_.coords.length).sum} intelligent $intelligentSample actual $actualSample"
                )
                Source(es.toList.map(_.sample(actualSample)))
              }
          val gpsHistory =
            Source.future(deviceService.db.history(user)).flatMapConcat { es =>
              Source(es.toList)
            }
          val formatter = TimeFormatter(user.language)
          val eventSource = history
            .merge(gpsHistory)
            .concat(
              boatService
                .clientEvents(formatter)
                .merge(deviceService.clientEvents(formatter))
            )
            .filter(_.isIntendedFor(username))
          // disconnects viewers that lag more than 3s
          val flow = Flow
            .fromSinkAndSource(Sink.ignore, eventSource)
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
    code: UserRequest[UserInfo, BoatName] => Future[BoatRow]
  ): Action[BoatName] =
    formAction(boatNameForm) { req =>
      code(req).map { b =>
        BoatResponse(b.toBoat)
      }
    }

  private def trackAction[F](
    form: Form[F]
  )(code: UserRequest[UserInfo, F] => Future[JoinedTrack]): Action[F] =
    formAction(form) { req =>
      val formatter = TimeFormatter(req.user.language)
      code(req).map { t =>
        TrackResponse(t.strip(formatter))
      }
    }

  private def formAction[T, W: Writeable](
    form: Form[T]
  )(code: UserRequest[UserInfo, T] => Future[W]): Action[T] =
    formActionResult(form) { r =>
      code(r).map { w =>
        Ok(w)
      }
    }

  private def formActionResult[T](
    form: Form[T]
  )(code: UserRequest[UserInfo, T] => Future[Result]): Action[T] =
    parsedAuth(parse.form(form, onErrors = (err: Form[T]) => formError(err)))(
      profile
    ) { req =>
      code(req)
    }

  private def logTermination[In, Out, Mat](
    flow: Flow[In, Out, Mat],
    message: Try[Done] => String
  ): Flow[In, Out, Future[Done]] =
    terminationWatched(flow)(t => fut(log.info(message(t))))

  private def secureTrack(
    run: BoatRequest[TrackQuery, MinimalUserInfo] => Future[Result]
  ) =
    secureAction(rh => TrackQuery.withDefault(rh, defaultLimit = 100))(run)

  private def secureJson[T, W: Writes](
    parse: RequestHeader => Either[SingleError, T]
  )(run: BoatRequest[T, MinimalUserInfo] => Future[W]) =
    secureAction(parse) { req =>
      run(req).map { w =>
        Ok(Json.toJson(w))
      }
    }

  private def secureAction[T](
    parse: RequestHeader => Either[SingleError, T]
  )(run: BoatRequest[T, MinimalUserInfo] => Future[Result]) =
    userAction(authTypical, parse)(run)

  private def userAction[T, U](
    authUser: RequestHeader => Future[U],
    parse: RequestHeader => Either[SingleError, T]
  )(run: BoatRequest[T, U] => Future[Result]) =
    authAction(authUser) { req =>
      parse(req.req).fold(
        err => fut(badRequest(err)),
        t =>
          run(AnyBoatRequest(req.user, t, req.req)).recover {
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

  private def terminationWatched[In, Out, Mat](
    flow: Flow[In, Out, Mat]
  )(onTermination: Try[Done] => Future[Unit]): Flow[In, Out, Future[Done]] =
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
    recovered(boatAuth(rh).flatMap(meta => db.joinAsBoat(meta)), rh)

  private def authDevice(
    rh: RequestHeader
  ): Future[Either[Result, JoinedBoat]] =
    recovered(boatAuthNoTrack(rh).flatMap(meta => db.joinAsDevice(meta)), rh)

  private def boatAuth(rh: RequestHeader): Future[BoatTrackMeta] =
    boatAuthNoTrack(rh).map { b =>
      b.withTrack(trackOrRandom(rh))
    }

  private def boatAuthNoTrack(rh: RequestHeader): Future[DeviceMeta] =
    rh.headers
      .get(BoatTokenHeader)
      .map { token =>
        auther.authBoat(BoatToken(token))
      }
      .getOrElse {
        val boatName =
          rh.headers
            .get(BoatNameHeader)
            .map(BoatName.apply)
            .getOrElse(BoatNames.random())
        fut(SimpleBoatMeta(anonUser, boatName))
      }

  private def authOrAnon(rh: RequestHeader): Future[MinimalUserInfo] =
    authMinimal(rh, _ => Future.successful(MinimalUserInfo.anon))

  private def authTypical(rh: RequestHeader): Future[MinimalUserInfo] =
    authMinimal(rh, mce => Future.failed(mce))

  private def authMinimal(
    rh: RequestHeader,
    onFail: MissingCredentialsException => Future[MinimalUserInfo]
  ): Future[MinimalUserInfo] =
    profile(rh).recoverWith {
      case mce: MissingCredentialsException =>
        authSessionUser(rh).map(Future.successful).getOrElse(onFail(mce))
    }

  private def authSessionUser(rh: RequestHeader): Option[MinimalUserInfo] =
    rh.session.get(UserSessionKey).filter(_ != Usernames.anon.name).map { user =>
      SimpleUserInfo(
        Username(user),
        rh.session
          .get(LanguageSessionKey)
          .map(Language.apply)
          .getOrElse(Language.default)
      )
    }

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
