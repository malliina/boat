package com.malliina.boat.http4s

import cats.data.NonEmptyList
import cats.effect.kernel.Async
import cats.effect.{IO, Sync}
import cats.implicits.catsSyntaxFlatten
import cats.syntax.all.{catsSyntaxApplicativeError, toFlatMapOps, toFunctorOps}
import com.malliina.assets.HashedAssets
import com.malliina.boat.Constants.{LanguageName, TokenCookieName}
import com.malliina.boat.*
import com.malliina.boat.auth.AuthProvider.{PromptKey, SelectAccount}
import com.malliina.boat.auth.{AuthProvider, SettingsPayload, UserPayload}
import com.malliina.boat.db.*
import com.malliina.boat.graph.*
import com.malliina.boat.html.{BoatHtml, BoatLang}
import com.malliina.boat.http.InviteResult.{AlreadyInvited, Invited, UnknownEmail}
import com.malliina.boat.http.*
import com.malliina.boat.http4s.BasicService.{cached, noCache, ranges}
import com.malliina.boat.http4s.Service.{RequestOps, log}
import com.malliina.boat.push.{BoatState, PushService}
import com.malliina.util.AppLogger
import com.malliina.values.{Email, Readable, UserId, Username}
import com.malliina.web.OAuthKeys.{Nonce, State}
import com.malliina.web.Utils.randomString
import com.malliina.web.*
import com.malliina.boat.auth.BoatJwt
import com.malliina.boat.parsing.{CarCoord, CarStats}
import fs2.{Pipe, Stream}
import io.circe.parser.parse
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Json}
import org.http4s.headers.{Location, `Content-Type`, `WWW-Authenticate`}
import org.http4s.implicits.uri
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Text
import org.http4s.{Callback as _, *}
import org.typelevel.ci.CIStringSyntax

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.DurationInt

object Service:
  private val log = AppLogger(getClass)

  implicit class RequestOps[F[_]](val req: Request[F]) extends AnyVal:
    def isSecured: Boolean = Urls.isSecure(req)

class Service[F[_]: Async](comps: BoatComps[F]) extends BasicService[F]:
  val F = Sync[F]
  val auth = comps.auth
  val userMgmt = auth.users
  val html = comps.html
  val db = comps.db
  val inserts = comps.inserts
  val streams = comps.streams
  val deviceStreams = comps.devices
  val cars = comps.cars
  val push = comps.push
  val web = auth.web
  val cookieNames = web.cookieNames
  val reverse = Reverse
  val g = Graph.all
  private val NoChange = "No change."

  private val toClients: Stream[F, WebSocketFrame] = Stream.never[F]
  private val pings =
    Stream.awakeEvery(30.seconds).map(d => PingEvent(System.currentTimeMillis(), d))

  def routes(sockets: WebSocketBuilder2[F]): HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ GET -> Root      => index(req)
    case GET -> Root / "health" => ok(AppMeta.default)
    case GET -> Root / "favicon.ico" =>
      temporaryRedirect(Uri.unsafeFromString(s"/${BoatHtml.faviconPath}"))
    case req @ GET -> Root / "pingAuth" =>
      auth.profile(req).flatMap { _ => ok(AppMeta.default) }
    case req @ GET -> Root / "users" / "me" =>
      auth.profile(req).flatMap { user => ok(UserContainer(user)) }
    case req @ POST -> Root / "users" / "me" =>
      req.as[RegisterCode](implicitly, jsonBody[F, RegisterCode]).flatMap { reg =>
        auth.register(reg.code, Instant.now()).flatMap { boatJwt =>
          ok(boatJwt)
        }
      }
    case req @ POST -> Root / "users" / "me" / "tokens" =>
      auth.recreate(req.headers).flatMap { boatJwt =>
        ok(boatJwt)
      }
    case req @ PUT -> Root / "users" / "me" =>
      jsonAction[ChangeLanguage](req) { (newLanguage, user) =>
        userMgmt.changeLanguage(user.id, newLanguage.language).flatMap { changed =>
          val msg = if changed then s"Changed language to ${newLanguage.language}." else NoChange
          ok(SimpleMessage(msg))
        }
      }
    case req @ POST -> Root / "users" / "me" / "delete" =>
      auth.delete(req.headers, Instant.now()).flatMap { _ =>
        ok(SimpleMessage("Deleted."))
      }
    case req @ POST -> Root / "users" / "notifications" =>
      jsonAction[PushPayload](req) { (payload, user) =>
        push.enable(PushInput(payload.token, payload.device, user.id)).flatMap { _ =>
          ok(SimpleMessage("Enabled."))
        }
      }
    case req @ POST -> Root / "users" / "notifications" / "disable" =>
      jsonAction[DisablePush](req) { (payload, user) =>
        push.disable(payload.token, user.id).flatMap { disabled =>
          val msg = if disabled then "Disabled." else NoChange
          ok(SimpleMessage(msg))
        }
      }
    case req @ POST -> Root / "invites" =>
      formAction[InvitePayload](req, forms.invite) { (inviteInfo, user) =>
        userMgmt.invite(inviteInfo.byUser(user.id)).flatMap { res =>
          val message = res match
            case UnknownEmail(email) =>
              s"Unknown email: '$email'."
            case Invited(uid, to) =>
              s"User ${user.email} invited ${inviteInfo.email} to boat ${inviteInfo.boat}."
            case AlreadyInvited(uid, to) =>
              s"User ${inviteInfo.email} already invited to ${inviteInfo.boat}."
          log.info(message)
          seeOther(reverse.boats)
        }
      }
    case req @ POST -> Root / "invites" / "revoke" =>
      formAction[RevokeAccess](req, forms.revokeInvite) { (revokeInfo, user) =>
        userMgmt.revokeAccess(revokeInfo.to, revokeInfo.from, user.id).flatMap { res =>
          seeOther(reverse.boats)
        }
      }
    case req @ POST -> Root / "invites" / "respond" =>
      formAction[InviteResponse](req, forms.respondInvite) { (response, user) =>
        userMgmt
          .updateInvite(
            response.to,
            user.id,
            if response.accept then InviteState.Accepted else InviteState.Rejected
          )
          .flatMap { res =>
            seeOther(reverse.boats)
          }
      }
    case req @ GET -> Root / "conf" =>
      respondCustom(req)(
        json = rs =>
          val conf =
            if rs.exists(_.satisfies(ContentVersions.Version2)) then ClientConf.default
            else ClientConf.old
          ok(conf)
        ,
        html = ok(ClientConf.default)
      )
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
          json = rs =>
            db.tracksFor(authed.user, authed.query).flatMap { ts =>
              if rs.exists(_.satisfies(ContentVersions.Version2)) then ok(ts)
              else if rs.exists(_.satisfies(ContentVersions.Version1)) then
                ok(TrackSummaries(ts.tracks.map(t => TrackSummary(t))))
              else ok(ts)
            },
          html =
            val lang = BoatLang(authed.user.language).lang
            db.tracksBundle(authed.user, authed.query, lang).flatMap { ts =>
              ok(html.tracks(ts, authed.query, lang))
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
      def readTitle(form: FormReader) =
        form.read[TrackTitle](TrackTitle.Key).map(ChangeTrackTitle.apply)
      trackAction[ChangeTrackTitle](req, readTitle) { (title, user) =>
        inserts.updateTitle(trackName, title.title, user.id)
      }
    case req @ PATCH -> Root / "tracks" / TrackIdVar(trackId) =>
      def readComments(form: FormReader) =
        form.read[String](TrackComments.Key).map(ChangeComments.apply)
      trackAction[ChangeComments](req, readComments) { (comments, user) =>
        inserts.updateComments(trackId, comments.comments, user.id)
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
      for
        authed <- authedLimited(req)
        lang = authed.user.language
        ref <- db.ref(trackName, lang)
        response <- ok(html.chart(ref, BoatLang(lang)))
      yield response
    case req @ GET -> Root / "vessels" =>
      val handler = for
        authed <- authedQuery(req, VesselQuery.query)
        rows <- comps.vessels.load(authed.query)
        response <- respond(req)(
          json = ok(rows),
          html = ok(
            html.map(
              authed.user.userBoats,
              rows.headOption.flatMap(_.updates.headOption.map(_.coord))
            )
          )
        )
      yield response
      handler.recoverWith { case t =>
        redirectToLogin
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
        F.blocking(g.shortest(req.from, req.to)).flatMap { op =>
          op.map { result => ok(result) }.recover {
            case NoRoute(f, t)     => notFound(Errors(s"No route found from '$f' to '$t'."))
            case UnresolvedFrom(f) => badRequest(Errors(s"Unresolvable from '$f'."))
            case UnresolvedTo(t)   => badRequest(Errors(s"Unresolvable to '$t'."))
            case EmptyGraph        => serverError(Errors("Graph engine not available."))
          }
        }
      }.recover { err =>
        badRequest(Errors(err.message))
      }
    case req @ GET -> Root / "ws" / "updates" =>
      auth.authOrAnon(req.headers).flatMap { user =>
        val username = user.username
        log.info(s"Viewer '${user.username}' joined.")
        BoatQuery(req.uri.query).map { boatQuery =>
          log.info(s"Got $boatQuery")
          val historicalLimits =
            if boatQuery.tracks.nonEmpty && username == Usernames.anon then
              BoatQuery.tracks(boatQuery.tracks)
            else if username == Usernames.anon then BoatQuery.empty
            else boatQuery
          val historyIO = db.history(user, historicalLimits).flatMap { es =>
            // unless a sample is specified, return about 300 historical points - this optimization is for charts
            val intelligentSample = math.max(1, es.map(_.coords.length).sum / 300)
            val actualSample = boatQuery.sample.getOrElse(intelligentSample)
            log.debug(
              s"Points ${es.map(_.coords.length).sum} intelligent $intelligentSample actual $actualSample"
            )
//            F.pure(es.toList.map(_.sample(actualSample)))
            F.pure(es.toList)
          }
          val boatHistory = Stream.evalSeq(historyIO)
          val gpsHistory = Stream.evalSeq(deviceStreams.db.history(user))
//          val recentTime = TimeRange.recent(Instant.now().minus(48, ChronoUnit.HOURS))
//          val carTime =
//            if historicalLimits.timeRange == TimeRange.none then recentTime
//            else historicalLimits.timeRange
//          val carHistoryIO = cars.history(CarQuery(historicalLimits.limits, carTime, Nil), user)
//          val carHistory = Stream.evalSeq(carHistoryIO)
          val formatter = TimeFormatter.lang(user.language)
          val boatUpdates = streams.clientEvents(formatter)
          val gpsUpdates = deviceStreams.clientEvents(formatter)
//          val carUpdates = cars.insertions.subscribe(100)
          val eventSource =
            ((boatHistory ++ gpsHistory) ++ boatUpdates
              .mergeHaltBoth(gpsUpdates))
//              .mergeHaltBoth(carUpdates))
              .mergeHaltBoth(pings)
              .filter(_.isIntendedFor(user))
              .map(message => Text(message.asJson.noSpaces))
          webSocket(
            sockets,
            eventSource,
            message => F.delay(log.info(message)),
            onClose = F.delay(log.info(s"Viewer '$username' left."))
          )
        }.recover { errors =>
          badRequest(errors)
        }
      }
    case req @ GET -> Root / "ws" / "boats" =>
      auth
        .authBoat(req.headers)
        .flatMap { boat =>
          log.info(s"Boat '${boat.boat}' by '${boat.user}' connected.")
          inserts.joinAsBoat(boat).flatMap { meta =>
            push
              .push(meta, BoatState.Connected)
              .as[Unit](())
              .handleErrorWith { t =>
                F.delay(log.error(s"Failed to push all device notifications.", t))
              }
              .flatMap { _ =>
                webSocket(
                  sockets,
                  toClients,
                  message =>
                    log.debug(s"Boat '${boat.boat}' by '${boat.user}' says '$message'.")
                    val parsed = parseUnsafe(message)
                    streams.boatIn
                      .publish1(BoatEvent(parsed, meta))
                      .map(e =>
                        e.fold(
                          err =>
                            log
                              .warn(s"Failed to publish '$message' by ${boat.boat}, topic closed."),
                          identity
                        )
                      )
                  ,
                  onClose =
                    F.delay(log.info(s"Boat '${boat.boat}' by '${boat.user}' left.")).flatMap { _ =>
                      push.push(meta, BoatState.Disconnected).map(_ => ())
                    }
                ).onError { t =>
                  F.delay(
                    log.info(s"Boat '${boat.boat}' by '${boat.user}' left exceptionally.", t)
                  ).flatMap { _ =>
                    push.push(meta, BoatState.Disconnected).map(_ => ())
                  }
                }
              }
          }
        }
    case req @ GET -> Root / "ws" / "devices" =>
      auth.authDevice(req.headers).flatMap { meta =>
        inserts.joinAsDevice(meta).flatMap { boat =>
          webSocket(
            sockets,
            toClients,
            message =>
              deviceStreams.in
                .publish1(DeviceEvent(parseUnsafe(message), boat))
                .map(
                  _.fold(
                    err => log.warn(s"Failed to publish '$message', topic closed."),
                    identity
                  )
                ),
            onClose = F.delay(log.info(s"Device '${boat.boatName}' by '${boat.username}' left."))
          )
        }
      }
    case req @ GET -> Root / "cars" / "history" =>
      for
        authed <- authedQuery(req, BoatQuery.car)
        drives <- cars.history(authed.query, authed.user)
        res <- respond(req)(
          json = ok(CarHistoryResponse(drives)),
          html = ok(html.carHistory(drives))
        )
      yield res
    case req @ POST -> Root / "cars" / "locations" =>
      jsonAction[LocationUpdates](req) { (body, user) =>
        user.boats
          .find(_.id == body.carId)
          .map { device =>
            import cats.implicits.*
            val meta = SimpleBoatMeta(user.username, device.name)
            inserts.joinAsBoat(meta).flatMap { meta =>
              val count = body.updates.size
              log.debug(s"User ${user.email} POSTs $count car updates...")
              body.updates.traverse { loc =>
                val in = CarCoord.fromUpdate(loc, meta.track)
                inserts.saveCoords(in)
              }.flatMap { inserteds =>
                ok(SimpleMessage(s"Saved ${inserteds.size} updates."))
              }
            }
          }
          .getOrElse {
            badRequest(Errors(SingleError.input(s"Invalid car ID: '${body.carId}'.")))
          }
      }
    case req @ GET -> Root / "sign-in" =>
      val now = Instant.now()
      req.cookies
        .find(_.name == cookieNames.provider)
        .flatMap { cookie =>
          AuthProvider.forString(cookie.content).toOption
        }
        .map { provider =>
          if provider == AuthProvider.Apple then
            val recreation = web
              .parseLongTermCookie(req.headers)
              .map { idToken =>
                auth.webSiwa.recreate(idToken, now).flatMap { boatJwt =>
                  appleResult(boatJwt, req)
                }
              }
            recreation.getOrElse(temporaryRedirect(reverse.signInFlow(provider)))
          else temporaryRedirect(reverse.signInFlow(provider))
        }
        .getOrElse {
          ok(html.signIn(Lang.default))
        }
    case req @ GET -> Root / "sign-in" / "google" =>
      startHinted(AuthProvider.Google, auth.googleFlow, req)
    case req @ GET -> Root / "sign-in" / "microsoft" =>
      startHinted(AuthProvider.Microsoft, auth.microsoftFlow, req)
    case req @ GET -> Root / "sign-in" / "apple" =>
      start(auth.appleWebFlow, AuthProvider.Apple, req)
    case req @ GET -> Root / "sign-in" / "callbacks" / "google" =>
      handleAuthCallback(auth.googleFlow, AuthProvider.Google, req)
    case req @ GET -> Root / "sign-in" / "callbacks" / "microsoft" =>
      handleAuthCallback(auth.microsoftFlow, AuthProvider.Microsoft, req)
    case req @ POST -> Root / "sign-in" / "callbacks" / "apple" =>
      handleAppleCallback(req)
    case GET -> Root / "sign-out" =>
      SeeOther(Location(reverse.index)).map { res =>
        auth.web.clearSession(res)
      }
    case GET -> Root / "docs" / "agent"    => docsRedirect("agent")
    case GET -> Root / "docs" / "support"  => docsRedirect("support")
    case GET -> Root / "legal" / "privacy" => docsRedirect("privacy")
    case req @ GET -> Root / "files" =>
      comps.s3.files().flatMap { objects =>
        val urls = objects.map { obj =>
          Urls.hostOnly(req) / "files" / obj.key()
        }
        ok(Json.obj("files" -> urls.asJson))(jsonEncoder[F])
      }
    case GET -> Root / "files" / file =>
      comps.s3.download(file).flatMap { file =>
        val stream = fs2.io.file.Files[F].readAll(fs2.io.file.Path.fromNioPath(file))
//        val stream = fs2.io.readInputStream[F](F.pure(obj.getObjectContent), 8192)
        Ok(stream)
      }

    case req @ GET -> Root / ".well-known" / "apple-app-site-association" =>
      fileFromPublicResources("apple-app-site-association.json", req)
    case req @ GET -> Root / ".well-known" / "assetlinks.json" =>
      fileFromPublicResources("android-assetlinks.json", req)
    case req @ GET -> Root / TrackCanonicalVar(canonical) =>
      respond(req)(
        json = authedTrackQuery(req).flatMap { authed =>
          db.canonical(canonical, authed.user.language)
            .flatMap { ref =>
              ok(TrackResponse(ref))
            }
            .handleErrorWith { t =>
              val errors = Errors(s"Not found: '$canonical'.")
              log.info(s"${errors.message}", t)
              notFound(errors)
            }
        },
        html = index(req)
      )
  }

  private def parseUnsafe(message: String) =
    parse(message).fold(err => throw Exception(s"Not JSON: '$message'. $err"), identity)

  private object forms:
    import Readables.*
    def invite(form: FormReader) = for
      boat <- form.read[DeviceId](Forms.Boat)
      email <- form.read[Email](Forms.Email)(Readable.email)
    yield InvitePayload(boat, email)

    def respondInvite(form: FormReader) = for
      boat <- form.read[DeviceId](Forms.Boat)
      accept <- form.read[Boolean](Forms.Accept)
    yield InviteResponse(boat, accept)

    def revokeInvite(form: FormReader) = for
      boat <- form.read[DeviceId](Forms.Boat)
      user <- form.read[UserId](Forms.User)
    yield RevokeAccess(boat, user)

  private def webSocket(
    sockets: WebSocketBuilder2[F],
    toClient: Stream[F, WebSocketFrame],
    onMessage: String => F[Unit],
    onClose: F[Unit]
  ) =
    val fromClient: Pipe[F, WebSocketFrame, Unit] = _.evalMap {
      case Text(message, _) =>
        log.debug(s"Message $message")
        onMessage(message)
      case f =>
        F.delay(log.debug(s"Unknown WebSocket frame: $f"))
    }
    sockets.withOnClose(onClose).build(toClient, fromClient)

  private def index(req: Request[F]) =
    auth.optionalWebAuth(req).flatMap { result =>
      result.map { userRequest =>
        val maybeBoat = userRequest.user
        val u: Username = maybeBoat.map(_.user).getOrElse(Usernames.anon)
        val lang = maybeBoat.map(_.language).getOrElse(Language.default)
        val authorizedBoats = maybeBoat.map(_.boats.map(_.boat)).getOrElse(Nil)
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
          auth.saveSettings(SettingsPayload(u, lang, authorizedBoats), cookied, isSecure)
        }
      }.recover { mc =>
        redirectToLogin
      }
    }

  private def trackAction[T: Decoder](req: Request[F], readForm: FormReader => Either[Errors, T])(
    code: (T, UserInfo) => F[JoinedTrack]
  ) =
    formAction[T](req, readForm) { (t, user) =>
      code(t, user).flatMap { track =>
        val formatter = TimeFormatter.lang(user.language)
        ok(TrackResponse(track.strip(formatter)))
      }
    }

  implicit val boatNameReader: Readable[BoatName] =
    Readable.string.emap(s => BoatName.build(s.trim))

  private def boatFormAction(req: Request[F])(code: (BoatName, UserInfo) => F[BoatRow]) =
    formAction(
      req,
      form => form.read[BoatName](BoatNames.Key)
    ) { (boatName, user) =>
      code(boatName, user).flatMap { row =>
        respond(req)(
          json = ok(BoatResponse(row.toBoat)),
          html = SeeOther(Location(reverse.boats))
        )
      }
    }

  private def formAction[T: Decoder](req: Request[F], readForm: FormReader => Either[Errors, T])(
    code: (T, UserInfo) => F[Response[F]]
  )(implicit decoder: EntityDecoder[F, UrlForm]): F[Response[F]] =
    auth.profile(req).flatMap { user =>
      val isForm = req.headers
        .get(ci"Content-Type")
        .exists(_.head.value == "application/x-www-form-urlencoded")
      val decoded =
        if isForm then
          decoder
            .decode(req, strict = false)
            .foldF(
              fail => F.raiseError(fail),
              form =>
                readForm(new FormReader(form)).fold(
                  err => F.raiseError(err.asException),
                  F.pure
                )
            )
        else req.decodeJson[T]
      decoded.flatMap { t =>
        code(t, user)
      }.handleErrorWith { err =>
        log.error(s"Form failure. $err")
        badRequest(Errors(SingleError.input("Invalid form input.")))
      }
    }

  private def jsonAction[T: Decoder](req: Request[F])(
    code: (T, UserInfo) => F[Response[F]]
  ) =
    auth.profile(req).flatMap { user =>
      req.as[T](implicitly, jsonBody[F, T]).flatMap { t =>
        code(t, user)
      }
    }

  private def respond(
    req: Request[F]
  )(json: F[Response[F]], html: F[Response[F]]): F[Response[F]] =
    respondCustom(req)(_ => json, html)

  private def respondCustom(req: Request[F])(
    json: NonEmptyList[MediaRange] => F[Response[F]],
    html: F[Response[F]]
  ): F[Response[F]] =
    val rs = ranges(req.headers)
    val qp = req.uri.query.params
    if rs.exists(_.satisfies(MediaType.text.html)) && !qp.contains("json") then html
    else json(rs)

  private def fileFromPublicResources(file: String, req: Request[F]): F[Response[F]] =
    StaticFile
      .fromResource(
        s"public/$file",
        Option(req),
        preferGzipped = true
      )
      .fold(notFoundReq(req))(res => F.pure(res.putHeaders(cached(1.hour))))
      .flatten

  private def docsRedirect(name: String) = SeeOther(
    Location(uri"https://docs.boat-tracker.com/".addPath(s"$name/"))
  )

  private def authedLimited(req: Request[F]) =
    authedAndParsed(req, auth.typical, TrackQuery.withDefault(_, 100))

  private def authedTrackQuery(req: Request[F]): F[BoatRequest[TrackQuery, MinimalUserInfo]] =
    authedAndParsed(req, auth.typical, TrackQuery.apply)

  private def authedQuery[T](
    req: Request[F],
    query: Query => Either[Errors, T]
  ): F[BoatRequest[T, UserInfo]] =
    authedAndParsed[T, UserInfo](req, hs => auth.profile(hs, Instant.now()), query)

  private def authedAndParsed[T, U](
    req: Request[F],
    makeAuth: Headers => F[U],
    query: Query => Either[Errors, T]
  ): F[BoatRequest[T, U]] =
    makeAuth(req.headers).flatMap { user =>
      query(req.uri.query).fold(
        err => F.raiseError(InvalidRequest(req, err)),
        q => F.pure(AnyBoatRequest(user, q, req.headers))
      )
    }

  private def start(validator: FlowStart[F], provider: AuthProvider, req: Request[F]) =
    validator
      .start(Urls.hostOnly(req) / reverse.signInCallback(provider).renderString, Map.empty)
      .flatMap { s =>
        startLoginFlow(s, req.isSecured)
      }

  private def startHinted(
    provider: AuthProvider,
    validator: LoginHint[F],
    req: Request[F]
  ): F[Response[F]] = F.delay {
    val redirectUrl = Urls.hostOnly(req) / reverse.signInCallback(provider).renderString
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
  }.flatMap { case (redirectUrl, maybeEmail, extra) =>
    validator.startHinted(redirectUrl, maybeEmail, extra).flatMap { s =>
      startLoginFlow(s, req.isSecured)
    }
  }

  private def startLoginFlow(s: Start, isSecure: Boolean): F[Response[F]] = F.delay {
    val state = randomString()
    val encodedParams = (s.params ++ Map(OAuthKeys.State -> state)).map { case (k, v) =>
      k -> com.malliina.web.Utils.urlEncode(v)
    }
    val url = s.authorizationEndpoint.append(s"?${stringify(encodedParams)}")
    log.info(s"Redirecting to '$url' with state '$state'...")
    val sessionParams: Seq[(String, String)] = Seq(State -> state) ++ s.nonce
      .map(n => Seq(Nonce -> n))
      .getOrElse(Nil)
    (url, sessionParams)
  }.flatMap { case (url, sessionParams) =>
    SeeOther(Location(Uri.unsafeFromString(url.url))).map { res =>
      val session = sessionParams.toMap.asJson
      auth.web
        .withSession(session, isSecure, res)
        .putHeaders(noCache)
    }
  }

  private def handleAuthCallback(
    flow: DiscoveringAuthFlow[F, Email],
    provider: AuthProvider,
    req: Request[F]
  ) = handleCallback(
    req,
    provider,
    cb =>
      flow.validateCallback(cb).map { e =>
        e.flatMap { v =>
          flow.parse(v)
        }
      }
  )

  private def handleCallback(
    req: Request[F],
    provider: AuthProvider,
    validate: Callback => F[Either[AuthError, Email]]
  ): F[Response[F]] =
    val params = req.uri.query.params
    val session = web.authState[Map[String, String]](req.headers).toOption.getOrElse(Map.empty)
    val cb = Callback(
      params.get(OAuthKeys.State),
      session.get(State),
      params.get(OAuthKeys.CodeKey),
      session.get(Nonce),
      Urls.hostOnly(req) / reverse.signInCallback(provider).renderString
    )
    validate(cb).flatMap { e =>
      e.fold(
        err => unauthorized(Errors(err.message.message)),
        email => userResult(email, provider, req)
      )
    }

  private def handleAppleCallback(req: Request[F])(implicit
    decoder: EntityDecoder[F, UrlForm]
  ): F[Response[F]] =
    decoder
      .decode(req, strict = false)
      .foldF(
        failure => unauthorized(Errors(failure.message)),
        urlForm =>
          AppleResponse(urlForm).map { form =>
            val session =
              web.authState[Map[String, String]](req.headers).toOption.getOrElse(Map.empty)
            val actualState = form.state
            val sessionState = session.get(State)
            if sessionState.contains(actualState) then
              val redirectUrl =
                Urls.hostOnly(req) / reverse.signInCallback(AuthProvider.Apple).renderString
              auth.webSiwa.registerWeb(form.code, Instant.now(), redirectUrl).flatMap { boatJwt =>
                appleResult(boatJwt, req)
              }
            else
              val detailed =
                sessionState.fold(s"Got '$actualState' but found nothing to compare to.") {
                  expected =>
                    s"Got '$actualState' but expected '$expected'."
                }
              log.error(s"Authentication failed, state mismatch. $detailed $req")
              unauthorized(Errors("State mismatch."))
          }.recover { err =>
            unauthorized(Errors(err))
          }
      )

  private def appleResult(boatJwt: BoatJwt, req: Request[F]) =
    userResult(boatJwt.email, AuthProvider.Apple, req).map { res =>
      res.addCookie(web.longTermCookie(boatJwt.idToken))
    }

  private def userResult(
    email: Email,
    provider: AuthProvider,
    req: Request[F]
  ): F[Response[F]] =
    val returnUri: Uri = req.cookies
      .find(_.name == cookieNames.returnUri)
      .flatMap(c => Uri.fromString(c.content).toOption)
      .getOrElse(reverse.index)
    SeeOther(Location(returnUri)).map { r =>
      web.withAppUser(UserPayload.email(email), Urls.isSecure(req), provider, r)
    }

  def stringify(map: Map[String, String]): String =
    map.map { case (key, value) => s"$key=$value" }.mkString("&")

  private def unauthorized(errors: Errors) = redirectToLogin
  private def redirectToLogin: F[Response[F]] = SeeOther(Location(reverse.signIn))
