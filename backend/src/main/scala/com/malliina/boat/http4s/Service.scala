package com.malliina.boat.http4s

import cats.data.NonEmptyList
import cats.effect.{Blocker, ContextShift, IO}
import cats.implicits.catsSyntaxFlatten
import com.malliina.boat.Constants.{LanguageName, TokenCookieName}
import com.malliina.boat.{Utils => BoatUtils, _}
import com.malliina.boat.auth.AuthProvider.{PromptKey, SelectAccount}
import com.malliina.boat.auth.{AuthProvider, SettingsPayload, UserPayload}
import com.malliina.boat.db.{BoatRow, IdentityError, PushInput, StatsSource, TrackInsertsDatabase, TracksSource}
import com.malliina.boat.graph._
import com.malliina.boat.html.{BoatHtml, BoatLang}
import com.malliina.boat.http._
import com.malliina.boat.http4s.BasicService.{cached, noCache, ranges}
import com.malliina.boat.http4s.Service.{BoatComps, log}
import com.malliina.boat.push.{BoatState, PushService}
import com.malliina.util.AppLogger
import com.malliina.values.{Email, Username}
import com.malliina.web.OAuthKeys.{Nonce, State}
import com.malliina.web.Utils.randomString
import com.malliina.web._
import fs2.Pipe
import org.http4s.headers.{Location, `WWW-Authenticate`}
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Text
import play.api.libs.json.{Json, Reads}
import org.http4s.{Callback => _, _}

import scala.concurrent.duration.DurationInt

object Service {
  private val log = AppLogger(getClass)

  case class BoatComps(
    html: BoatHtml,
    db: TracksSource,
    inserts: TrackInsertsDatabase,
    stats: StatsSource,
    auth: AuthService,
    mapboxToken: AccessToken,
    s3: S3Client,
    push: PushService,
    streams: BoatStreams,
    devices: GPSStreams,
    blocker: Blocker,
    cs: ContextShift[IO]
  )

  def apply(comps: BoatComps): Service = new Service(comps)
}

class Service(comps: BoatComps) extends BasicService[IO] {
  implicit val cs: ContextShift[IO] = comps.cs
  val blocker = comps.blocker
  val auth = comps.auth
  val userMgmt = auth.users
  val html = comps.html
  val db = comps.db
  val inserts = comps.inserts
  val streams = comps.streams
  val deviceStreams = comps.devices
  val push = comps.push
  val web = auth.web
  val cookieNames = web.cookieNames
  val reverse = Reverse
  val g = Graph.all
  val NoChange = "No change."

  val toClients: fs2.Stream[IO, WebSocketFrame] = fs2.Stream.never[IO].map { _ =>
    Text(Json.stringify(Json.toJson(PingEvent(System.currentTimeMillis()))))
  }

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root      => index(req)
    case GET -> Root / "health" => ok(AppMeta.default)
    case req @ GET -> Root / "pingAuth" =>
      auth.profile(req).flatMap { _ => ok(AppMeta.default) }
    case req @ GET -> Root / "users" / "me" =>
      auth.profile(req).flatMap { user => ok(UserContainer(user)) }
    case req @ PUT -> Root / "users" / "me" =>
      jsonAction[ChangeLanguage](req) { (newLanguage, user) =>
        userMgmt.changeLanguage(user.id, newLanguage.language).flatMap { changed =>
          val msg = if (changed) s"Changed language to $newLanguage." else NoChange
          ok(SimpleMessage(msg))
        }
      }
    case req @ POST -> Root / "users" / "notifications" =>
      jsonAction[PushPayload](req) { (payload, user) =>
        push.enable(PushInput(payload.token, payload.device, user.id)).flatMap { _ =>
          ok(SimpleMessage("Enabled."))
        }
      }
    case req @ PUT -> Root / "users" / "notifications" / "disable" =>
      jsonAction[PushPayload](req) { (payload, user) =>
        push.disable(payload.token, user.id).flatMap { disabled =>
          val msg = if (disabled) "Disabled." else NoChange
          ok(SimpleMessage(msg))
        }
      }
    case GET -> Root / "conf" => ok(ClientConf.default)
    case req @ GET -> Root / "boats" =>
      auth.profile(req).flatMap { user =>
        ok(html.devices(user))
      }
    case req @ POST -> Root / "boats" =>
      boatFormAction(req) { (boatName, user) =>
        inserts.addBoat(boatName, user.id)
      }
    case req @ PATCH -> Root / "boats" / DeviceIdVar(device) =>
      boatFormAction(req) { (boatName, user) =>
        inserts.renameBoat(device, boatName, user.id)
      }
    case req @ POST -> Root / "boats" / DeviceIdVar(device) / "delete" =>
      auth.profile(req).flatMap { user =>
        inserts.removeDevice(device, user.id).flatMap { rows =>
          respond(req)(
            json = ok(SimpleMessage("Done.")),
            html = SeeOther(Location(reverse.boats))
          )
        }
      }
    case req @ GET -> Root / "tracks" =>
      authedQuery(req, TrackQuery.apply).flatMap { authed =>
        respondCustom(req)(
          json = rs => {
            db.tracksFor(authed.user, authed.query).flatMap { ts =>
              if (rs.exists(_.satisfies(ContentVersions.Version2)))
                ok(ts)
              else if (rs.exists(_.satisfies(ContentVersions.Version1)))
                ok(TrackSummaries(ts.tracks.map(t => TrackSummary(t))))
              else
                ok(ts)
            }
          },
          html = {
            val lang = BoatLang(authed.user.language).lang
            db.tracksBundle(authed.user, authed.query, lang).flatMap { ts =>
              ok(html.tracks(ts, authed.query, lang))
            }
          }
        )
      }
    case req @ GET -> Root / "history" =>
      authedQuery(req, BoatQuery.apply).flatMap { authed =>
        db.history(authed.user, authed.query).flatMap { ts => ok(ts) }
      }
    case req @ GET -> Root / "tracks" / TrackNameVar(trackName) =>
      respond(req)(
        json = authedTrackQuery(req).flatMap { authed =>
          db.ref(trackName, authed.user.language).flatMap { ref => ok(ref) }
        },
        html = index(req)
      )
    case req @ PUT -> Root / "tracks" / TrackNameVar(trackName) =>
      def readTitle(form: FormReader) = form.readOne(TrackTitle.Key, s => TrackTitle.build(s))
      trackAction(req, readTitle) { (title, user) =>
        inserts.updateTitle(trackName, title, user.id)
      }
    case req @ PATCH -> Root / "tracks" / TrackIdVar(trackId) =>
      def readComments(form: FormReader) = form.readOne(TrackComments.Key, s => Right(s))
      trackAction(req, readComments) { (comments, user) =>
        inserts.updateComments(trackId, comments, user.id)
      }
    case req @ GET -> Root / "tracks" / TrackNameVar(trackName) / "full" =>
      authedLimited(req).flatMap { authed =>
        db.full(trackName, authed.user.language, authed.query).flatMap { track =>
          respond(req)(
            json = ok(track),
            html = ok(html.list(track, authed.query.limits, BoatLang(authed.user.language)))
          )
        }
      }
    case req @ GET -> Root / "tracks" / TrackNameVar(trackName) / "chart" =>
      authedLimited(req).flatMap { authed =>
        val lang = authed.user.language
        db.ref(trackName, lang).flatMap { ref =>
          ok(html.chart(ref, BoatLang(lang)))
        }
      }
    case req @ GET -> Root / "stats" =>
      authedQuery(req, TrackQuery.apply).flatMap { authed =>
        comps.stats.stats(authed.user, authed.query, BoatLang(authed.user.language).lang).flatMap {
          statsResponse =>
            ok(statsResponse)
        }
      }
    case GET -> Root / "routes" / DoubleVar(srcLat) /
        DoubleVar(srcLng) / DoubleVar(destLat) / DoubleVar(destLng) =>
      RouteRequest(srcLat, srcLng, destLat, destLng).map { req =>
        // TODO Run on another thread
        g.shortest(req.from, req.to).map { result => ok(result) }.recover {
          case NoRoute(f, t)     => notFound(Errors(s"No route found from '$f' to '$t'."))
          case UnresolvedFrom(f) => badRequest(Errors(s"Unresolvable from '$f'."))
          case UnresolvedTo(t)   => badRequest(Errors(s"Unresolvable to '$t'."))
          case EmptyGraph        => serverError(Errors("Graph engine not available."))
        }
      }.recover { err =>
        badRequest(Errors(err.message))
      }
    case req @ GET -> Root / "ws" / "updates" =>
      auth.authOrAnon(req.headers).flatMap { user =>
        val username = user.username
        BoatQuery(req.uri.query).map { limits =>
          val historicalLimits =
            if (limits.tracks.nonEmpty && username == Usernames.anon)
              BoatQuery.tracks(limits.tracks)
            else if (username == Usernames.anon) BoatQuery.empty
            else limits
          val historyIO = db.history(user, historicalLimits).flatMap { es =>
            // unless a sample is specified, return about 300 historical points - this optimization is for charts
            val intelligentSample = math.max(1, es.map(_.coords.length).sum / 300)
            val actualSample = limits.sample.getOrElse(intelligentSample)
            log.debug(
              s"Points ${es.map(_.coords.length).sum} intelligent $intelligentSample actual $actualSample"
            )
            IO.pure(es.toList.map(_.sample(actualSample)))
          }
          val deviceHistory = fs2.Stream.evalSeq(historyIO)
          val gpsHistory = fs2.Stream.evalSeq(deviceStreams.db.history(user))
          val formatter = TimeFormatter(user.language)
          val updates = streams.clientEvents(formatter)
          val deviceUpdates = deviceStreams.clientEvents(formatter)
          val eventSource =
            ((deviceHistory ++ gpsHistory) ++ updates.mergeHaltBoth(deviceUpdates))
              .filter(_.isIntendedFor(username))
              .map(message => Text(Json.stringify(Json.toJson(message))))
          webSocket(
            eventSource,
            message => IO(log.info(message)),
            onClose = IO(log.info(s"Viewer '$username' left."))
          )
        }.recover { errors =>
          badRequest(errors)
        }
      }
    case req @ GET -> Root / "ws" / "boats" =>
      auth
        .authBoat(req.headers)
        .flatMap { boat =>
          val boatTrack = boat.withTrack(TrackNames.random())
          inserts.joinAsBoat(boatTrack).flatMap { meta =>
            push
              .push(meta, BoatState.Connected)
              .handleErrorWith { t =>
                IO(log.error(s"Failed to push all device notifications.", t))
              }
              .flatMap { _ =>
                webSocket(
                  toClients,
                  message => streams.boatIn.publish1(BoatEvent(Json.parse(message), meta)),
                  onClose = IO(log.info(s"Boat '${boat.boat}' by '${boat.user}' left.")).flatMap {
                    _ =>
                      push.push(meta, BoatState.Disconnected).map(_ => ())
                  }
                )
              }
          }
        }
//        .getOrElse {
//          IO(
//            log.info(s"Boat authentication failed. No credentials. Got headers ${req.headers}.")
//          ).flatMap { _ =>
//            unauthorized(Errors("Credentials required."))
//          }
//        }
    case req @ GET -> Root / "ws" / "devices" =>
      auth.authDevice(req.headers).flatMap { meta =>
        inserts.joinAsDevice(meta).flatMap { boat =>
          webSocket(
            toClients,
            message => deviceStreams.in.publish1(DeviceEvent(Json.parse(message), boat)),
            onClose = IO(log.info(s"Device '${boat.boatName}' by '${boat.username}' left."))
          )
        }
      }
    case req @ GET -> Root / "sign-in" / "google" =>
      startHinted(AuthProvider.Google, auth.flow, req)
    case req @ GET -> Root / "sign-in" / "callbacks" / "google" =>
      handleCallback(
        req,
        AuthProvider.Google,
        cb => auth.flow.validateCallback(cb).map(e => e.flatMap(auth.flow.parse))
      )
    case GET -> Root / "sign-out" =>
      SeeOther(Location(reverse.index)).map { res =>
        auth.web.clearSession(res)
      }
    case GET -> Root / "docs" / "agent"    => docsRedirect("agent")
    case GET -> Root / "docs" / "support"  => docsRedirect("support")
    case GET -> Root / "legal" / "privacy" => docsRedirect("privacy")
    case req @ GET -> Root / "files" =>
      val urls = comps.s3.files().map { summary =>
        Urls.hostOnly(req) / "files" / summary.getKey
      }
      ok(Json.toJson("files" -> urls))(jsonEncoder[IO])
    case GET -> Root / "files" / file =>
      val obj = comps.s3.download(file)
      val stream = fs2.io.readInputStream[IO](IO.pure(obj.getObjectContent), 8192, comps.blocker)
      Ok(stream)
    case req @ GET -> Root / ".well-known" / "apple-developer-domain-association.txt" =>
      fileFromResources("apple-developer-domain-association.txt", req)
    case req @ GET -> Root / ".well-known" / "assetlinks.json" =>
      fileFromResources("assetlinks.json", req)
    case req @ GET -> Root / TrackCanonicalVar(canonical) =>
      respond(req)(
        json = authedTrackQuery(req).flatMap { authed =>
          db.canonical(canonical, authed.user.language).flatMap { ref =>
            ok(TrackResponse(ref))
          }
        },
        html = index(req)
      )
  }

  private def webSocket[T](
    toClient: fs2.Stream[IO, WebSocketFrame],
    onMessage: String => IO[Unit],
    onClose: IO[Unit]
  ) = {
    val fromClient: Pipe[IO, WebSocketFrame, Unit] = _.evalMap {
      case Text(message, _) =>
        onMessage(message)
      case f =>
        IO(log.debug(s"Unknown WebSocket frame: $f"))
    }
    WebSocketBuilder[IO].build(toClient, fromClient, onClose = onClose)
  }

  private def index(req: Request[IO]) =
    auth.optionalWebAuth(req).flatMap { userRequest =>
      val maybeBoat = userRequest.user
      val u: Username = maybeBoat.map(_.user).getOrElse(Usernames.anon)
      val lang = maybeBoat.map(_.language).getOrElse(Language.default)
      val isSecure = Urls.isSecure(req)
      val tokenCookie = ResponseCookie(
        TokenCookieName,
        comps.mapboxToken.token,
        secure = isSecure,
        httpOnly = false
      )
      val languageCookie = ResponseCookie(
        LanguageName,
        maybeBoat.map(_.language).getOrElse(Language.default).code,
        secure = isSecure,
        httpOnly = false
      )
      ok(html.map(maybeBoat.getOrElse(UserBoats.anon))).map { res =>
        val cookied = res.addCookie(tokenCookie).addCookie(languageCookie)
        auth.saveSettings(SettingsPayload(u, lang), cookied, isSecure)
      }
    }

  private def trackAction[T](req: Request[IO], readForm: FormReader => Either[Errors, T])(
    code: (T, UserInfo) => IO[JoinedTrack]
  ) =
    formAction[T](req, readForm) { (t, user) =>
      code(t, user).flatMap { track =>
        val formatter = TimeFormatter(user.language)
        ok(TrackResponse(track.strip(formatter)))
      }
    }

  private def boatFormAction(req: Request[IO])(code: (BoatName, UserInfo) => IO[BoatRow]) =
    formAction(
      req,
      form =>
        form.read[BoatName](BoatNames.Key, s => BoatName.build(s.trim).left.map(e => Errors(e)))
    ) { (boatName, user) =>
      code(boatName, user).flatMap { row =>
        respond(req)(
          json = ok(BoatResponse(row.toBoat)),
          html = SeeOther(Location(reverse.boats))
        )
      }
    }

  private def formAction[T](req: Request[IO], readForm: FormReader => Either[Errors, T])(
    code: (T, UserInfo) => IO[Response[IO]]
  ) =
    auth.profile(req).flatMap { user =>
      req.decode[UrlForm] { form =>
        readForm(new FormReader(form)).map { t =>
          code(t, user)
        }.recover { err =>
          log.error(s"Form failure. $err")
          badRequest(Errors(SingleError.input("Invalid form input.")))
        }
      }
    }

  private def jsonAction[T: Reads](req: Request[IO])(
    code: (T, UserInfo) => IO[Response[IO]]
  ) =
    auth.profile(req).flatMap { user =>
      req.as[T](implicitly, jsonBody[IO, T]).flatMap { t =>
        code(t, user)
      }
    }

  private def respond(
    req: Request[IO]
  )(json: IO[Response[IO]], html: IO[Response[IO]]): IO[Response[IO]] =
    respondCustom(req)(_ => json, html)

  private def respondCustom(req: Request[IO])(
    json: NonEmptyList[MediaRange] => IO[Response[IO]],
    html: IO[Response[IO]]
  ): IO[Response[IO]] = {
    val rs = ranges(req.headers)
    val qp = req.uri.query.params
    if (rs.exists(_.satisfies(MediaType.text.html)) && !qp.contains("json")) html
    else json(rs)
  }

  def fileFromResources(file: String, req: Request[IO]): IO[Response[IO]] =
    StaticFile
      .fromResource(
        file,
        comps.blocker,
        Option(req),
        preferGzipped = true
      )
      .fold(notFoundReq(req))(res => IO.pure(res.putHeaders(cached(1.hour))))
      .flatten

  def docsRedirect(name: String) = SeeOther(
    Location(uri"https://docs.boat-tracker.com/".addPath(s"$name/"))
  )

  private def authedLimited(req: Request[IO]) =
    authedAndParsed(req, auth.typical, TrackQuery.withDefault(_, 100))

  private def authedTrackQuery(req: Request[IO]): IO[BoatRequest[TrackQuery, MinimalUserInfo]] =
    authedAndParsed(req, auth.typical, TrackQuery.apply)

  private def authedQuery[T, U](
    req: Request[IO],
    query: Query => Either[Errors, T]
  ): IO[BoatRequest[T, UserInfo]] =
    authedAndParsed[T, UserInfo](req, auth.profile, query)

  private def authedAndParsed[T, U](
    req: Request[IO],
    makeAuth: Headers => IO[U],
    query: Query => Either[Errors, T]
  ): IO[BoatRequest[T, U]] =
    makeAuth(req.headers).flatMap { user =>
      query(req.uri.query).fold(
        err => IO.raiseError(new InvalidRequest(req, err)),
        q => IO.pure(AnyBoatRequest(user, q, req.headers))
      )
    }

  private def startHinted(
    provider: AuthProvider,
    validator: LoginHint[IO],
    req: Request[IO]
  ): IO[Response[IO]] = IO {
    val redirectUrl = Urls.hostOnly(req) / reverse.googleCallback.renderString
    val lastIdCookie = req.cookies.find(_.name == cookieNames.lastId)
    val promptValue = req.cookies
      .find(_.name == cookieNames.prompt)
      .map(_.content)
      .orElse(Option(SelectAccount).filter(_ => lastIdCookie.isEmpty))
    val extra = promptValue.map(c => Map(PromptKey -> c)).getOrElse(Map.empty)
    val maybeEmail = lastIdCookie.map(_.content).filter(_ => extra.isEmpty)
    maybeEmail.foreach { hint =>
      log.info(s"Starting OAuth flow with $provider using login hint '$hint'...")
    }
    promptValue.foreach { prompt =>
      log.info(s"Starting OAuth flow with $provider using prompt '$prompt'...")
    }
    (redirectUrl, maybeEmail, extra)
  }.flatMap {
    case (redirectUrl, maybeEmail, extra) =>
      validator.startHinted(redirectUrl, maybeEmail, extra).flatMap { s =>
        startLoginFlow(s, Urls.isSecure(req))
      }
  }

  private def startLoginFlow(s: Start, isSecure: Boolean): IO[Response[IO]] = IO {
    val state = randomString()
    val encodedParams = (s.params ++ Map(OAuthKeys.State -> state)).map {
      case (k, v) =>
        k -> Utils.urlEncode(v)
    }
    val url = s.authorizationEndpoint.append(s"?${stringify(encodedParams)}")
    log.info(s"Redirecting to '$url' with state '$state'...")
    val sessionParams: Seq[(String, String)] = Seq(State -> state) ++ s.nonce
      .map(n => Seq(Nonce -> n))
      .getOrElse(Nil)
    (url, sessionParams)
  }.flatMap {
    case (url, sessionParams) =>
      SeeOther(Location(Uri.unsafeFromString(url.url))).map { res =>
        val session = Json.toJsObject(sessionParams.toMap)
        auth.web
          .withSession(session, isSecure, res)
          .putHeaders(noCache)
      }
  }

  private def handleCallback(
    req: Request[IO],
    provider: AuthProvider,
    validate: Callback => IO[Either[AuthError, Email]]
  ): IO[Response[IO]] = {
    val params = req.uri.query.params
    val session = web.authState[Map[String, String]](req.headers).toOption.getOrElse(Map.empty)
    val cb = Callback(
      params.get(OAuthKeys.State),
      session.get(State),
      params.get(OAuthKeys.CodeKey),
      session.get(Nonce),
      Urls.hostOnly(req) / reverse.googleCallback.renderString
    )
    validate(cb).flatMap { e =>
      e.fold(
        err => unauthorized(Errors(err.message.message)),
        email => userResult(email, provider, req)
      )
    }
  }

  private def userResult(
    email: Email,
    provider: AuthProvider,
    req: Request[IO]
  ): IO[Response[IO]] = {
    val returnUri: Uri = req.cookies
      .find(_.name == cookieNames.returnUri)
      .flatMap(c => Uri.fromString(c.content).toOption)
      .getOrElse(reverse.index)
    SeeOther(Location(returnUri)).map { r =>
      web.withAppUser(UserPayload.email(email), Urls.isSecure(req), provider, r)
    }
  }

  def stringify(map: Map[String, String]): String =
    map.map { case (key, value) => s"$key=$value" }.mkString("&")

  def buildMessage(req: UserRequest[_], message: String) =
    s"User '${req.user}' from '${req.req.remoteAddr.getOrElse("unknown")}' $message."

  def onUnauthorized(error: IdentityError): IO[Response[IO]] = {
    log.warn(error.message.message)
    unauthorized(Errors(s"Unauthorized."))
  }

  def unauthorized(errors: Errors) = SeeOther(Location(reverse.google))

  def unauthorizedEnd(errors: Errors) =
    Unauthorized(
      `WWW-Authenticate`(NonEmptyList.of(Challenge("myscheme", "myrealm"))),
      errors
    ).map(r => web.clearSession(r))
}
