package com.malliina.boat.http4s

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all.{catsSyntaxApplicativeError, catsSyntaxFlatMapOps, toFlatMapOps, toFunctorOps, toSemigroupKOps, toTraverseOps}
import com.malliina.boat.*
import com.malliina.boat.Constants.{LanguageName, TokenCookieName}
import com.malliina.boat.auth.AuthProvider.{PromptKey, SelectAccount}
import com.malliina.boat.auth.{AuthProvider, BoatJwt, SettingsPayload, UserPayload}
import com.malliina.boat.db.*
import com.malliina.boat.graph.*
import com.malliina.boat.html.{BoatHtml, BoatLang}
import com.malliina.boat.http.*
import com.malliina.boat.http.InviteResult.{AlreadyInvited, Invited, UnknownEmail}
import com.malliina.http4s.BasicService.noCache
import com.malliina.boat.http4s.BoatBasicService.{cached, ranges}
import com.malliina.boat.http4s.Service.{isSecured, log, userAgent}
import com.malliina.boat.parsing.CarCoord
import com.malliina.boat.push.{PushState, SourceState}
import com.malliina.http.{CSRFConf, Errors, SingleError}
import com.malliina.http4s.CSRFSupport
import com.malliina.measure.DistanceM
import com.malliina.polestar.Polestar
import com.malliina.util.AppLogger
import com.malliina.values.{Email, Readable, Username}
import com.malliina.web.*
import com.malliina.web.OAuthKeys.{Nonce, State}
import com.malliina.web.Utils.randomString
import fs2.io.file.Files
import fs2.{Pipe, Stream}
import io.circe.parser.parse
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Json}
import org.http4s.headers.{Location, `Content-Type`, `User-Agent`}
import org.http4s.implicits.http4sHeaderSyntax
import org.http4s.server.middleware.CSRF
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Text
import org.http4s.{Callback as _, *}
import org.typelevel.ci.CIString

import java.time.Instant
import scala.annotation.unused
import scala.concurrent.duration.DurationInt

object Service:
  private val log = AppLogger(getClass)

  extension [F[_]](req: Request[F])
    def isSecured: Boolean = Urls.isSecure(req)
    def userAgent = req.headers
      .get[`User-Agent`]
      .flatMap(h => UserAgent.build(h.value).toOption)

class Service[F[_]: {Async, Files}](
  comps: BoatComps[F],
  graph: Graph,
  val csrf: CSRF[F, F],
  val csrfConf: CSRFConf
) extends BoatBasicService[F]
  with BoatDecoders[F]
  with CSRFSupport[F]:
  val auth = comps.auth
  val userMgmt = auth.users
  private def html(req: Request[F]) = if isCar(req) then comps.carHtml else comps.boatHtml
  private def isCar(req: Request[F]): Boolean = Urls.hostOnly(req).host.endsWith("car-map.com")
  val db = comps.db
  val vessels = comps.vessels
  val inserts = comps.inserts
  val streams = comps.streams
  val push = comps.push
  val web = auth.web
  val cookieNames = web.cookieNames
  val reverse = Reverse
  private val NoChange = "No change."

  private val toClients: Stream[F, WebSocketFrame] = Stream.never[F]
  private val pings =
    Stream.awakeEvery(30.seconds).map(d => PingEvent(System.currentTimeMillis(), d))

  val normalRoutes: HttpRoutes[F] = HttpRoutes.of[F]:
    case req @ GET -> Root      => index(req)
    case GET -> Root / "health" => ok(AppMeta.default)
    case req @ GET -> Root / "favicon.ico" =>
      val sourceType = if isCar(req) then SourceType.Vehicle else SourceType.Boat
      temporaryRedirect(BoatHtml.faviconPath(sourceType))
    case req @ GET -> Root / "pingAuth" =>
      auth.profile(req).flatMap(_ => ok(AppMeta.default))
    case req @ GET -> Root / "users" / "me" =>
      auth
        .profile(req)
        .flatMap: user =>
          ok(UserContainer(user))
    case req @ POST -> Root / "users" / "me" =>
      req
        .decodeJson[RegisterCode]
        .flatMap: reg =>
          auth.register(reg.code, now()).flatMap(boatJwt => ok(boatJwt))
    case req @ POST -> Root / "users" / "me" / "tokens" =>
      auth.recreate(req.headers).flatMap(boatJwt => ok(boatJwt))
    case req @ PUT -> Root / "users" / "me" =>
      jsonAction[ChangeLanguage](req): input =>
        val newLanguage = input.payload
        userMgmt
          .changeLanguage(input.user.id, newLanguage.language)
          .flatMap: changed =>
            val msg = if changed then s"Changed language to ${newLanguage.language}." else NoChange
            ok(SimpleMessage(msg))
    case req @ POST -> Root / "users" / "me" / "delete" =>
      auth.delete(req.headers, now()).flatMap(_ => ok(SimpleMessage("Deleted.")))
    case req @ POST -> Root / "users" / "notifications" =>
      jsonAction[PushPayload](req): input =>
        val payload = input.payload
        val in = PushInput(
          payload.token,
          payload.device,
          payload.deviceId,
          payload.trackName,
          input.user.id
        )
        push
          .enable(in)
          .flatMap: _ =>
            ok(SimpleMessage("Enabled."))
    case req @ POST -> Root / "users" / "notifications" / "disable" =>
      jsonAction[DisablePush](req): input =>
        push
          .disable(input.payload.token, input.user.id)
          .flatMap: disabled =>
            val msg = if disabled then "Disabled." else NoChange
            ok(SimpleMessage(msg))
    case req @ POST -> Root / "invites" =>
      formAction[InvitePayload](req): (inviteInfo, user) =>
        userMgmt
          .invite(inviteInfo.byUser(user.id))
          .flatMap: res =>
            val message = res match
              case UnknownEmail(email) =>
                s"Unknown email: '$email'."
              case Invited(_, to) =>
                s"User ${user.email} invited ${inviteInfo.email} to boat ${inviteInfo.boat}."
              case AlreadyInvited(_, to) =>
                s"User ${inviteInfo.email} already invited to ${inviteInfo.boat}."
            log.info(message)
            seeOther(reverse.boats)
    case req @ POST -> Root / "invites" / "revoke" =>
      formAction[RevokeAccess](req): (revokeInfo, user) =>
        userMgmt
          .revokeAccess(revokeInfo.to, revokeInfo.from, user.id)
          .flatMap: _ =>
            seeOther(reverse.boats)
    case req @ POST -> Root / "invites" / "respond" =>
      formAction[InviteResponse](req): (response, user) =>
        userMgmt
          .updateInvite(
            response.to,
            user.id,
            if response.accept then InviteState.Accepted else InviteState.Rejected
          )
          .flatMap(_ => seeOther(reverse.boats))
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
      auth
        .profile(req)
        .flatMap: user =>
          comps.polestar
            .carsAndTelematics(user.id)
            .flatMap: cars =>
              csrfOk: token =>
                html(req).carsAndBoats(user, cars, token)
    case req @ GET -> Root / "boats" / "me" =>
      auth
        .boatTokenOrFail(req.headers)
        .flatMap: boat =>
          ok(DeviceContainer(boat.strip))
    case req @ POST -> Root / "boats" =>
      formAction[AddSource](req): (boat, user) =>
        inserts
          .addSource(boat.boatName, boat.sourceType, user.id)
          .flatMap(row => boatResponse(req, row))
    case req @ PATCH -> Root / "boats" / DeviceIdVar(device) =>
      updateBoat(req, device)
    case req @ GET -> Root / "boats" / DeviceIdVar(device) / "edit" =>
      auth
        .profile(req)
        .flatMap: user =>
          user.boats
            .find(_.id == device)
            .map: boat =>
              csrfOk: token =>
                html(req).editDevice(user, boat, token, csrfConf)
            .getOrElse:
              unauthorizedNoCache(Errors("No access."))
    case req @ POST -> Root / "boats" / DeviceIdVar(device) / "edit" =>
      updateBoat(req, device)
    case req @ POST -> Root / "boats" / DeviceIdVar(device) / "delete" =>
      auth
        .profile(req)
        .flatMap: user =>
          inserts
            .removeDevice(device, user.id)
            .flatMap: _ =>
              respond(req)(
                json = ok(SimpleMessage("Done.")),
                html = seeOther(reverse.boats)
              )
    case req @ GET -> Root / "tracks" =>
      authedQuery(req, TracksQuery.apply).flatMap: authed =>
        val query = authed.query
        val user = authed.user
        respondCustom(req)(
          json = rs =>
            db.tracksFor(user, query)
              .flatMap: ts =>
                if rs.exists(_.satisfies(ContentVersions.Version2)) then ok(ts)
                else if rs.exists(_.satisfies(ContentVersions.Version1)) then
                  ok(TrackSummaries(ts.tracks.map(t => TrackSummary(t))))
                else ok(ts),
          html =
            val lang = BoatLang(user.language)
            db.tracksBundle(user, query, lang.lang)
              .flatMap: ts =>
                ok(html(req).tracks(user, ts, query, lang))
        )
    case req @ GET -> Root / "history" =>
      authedQuery(req, BoatQuery.apply).flatMap: authed =>
        db.history(authed.user, authed.query).flatMap(ts => ok(ts))
    case req @ GET -> Root / "tracks" / TrackNameVar(trackName) =>
      respond(req)(
        json = authedTrackQuery(req).flatMap: authed =>
          db.ref(trackName, authed.user.language).flatMap(ref => ok(ref)),
        html = index(req)
      )
    case req @ PUT -> Root / "tracks" / TrackNameVar(trackName) =>
      trackAction[ChangeTrackTitle](req): (title, user) =>
        inserts.updateTitle(trackName, title.title, user.id)
    case req @ PATCH -> Root / "tracks" / TrackIdVar(trackId) =>
      trackAction[ChangeComments](req): (comments, user) =>
        inserts.updateComments(trackId, comments.comments, user.id)
    case req @ GET -> Root / "tracks" / TrackNameVar(trackName) / "full" =>
      authedLimited(req).flatMap: authed =>
        db.full(trackName, authed.user.language, authed.query)
          .flatMap: track =>
            respond(req)(
              json = ok(track),
              html = ok(html(req).list(track, authed.query.limits, BoatLang(authed.user.language)))
            )
    case req @ GET -> Root / "tracks" / TrackNameVar(trackName) / "chart" =>
      for
        authed <- authedLimited(req)
        lang = authed.user.language
        ref <- db.ref(trackName, lang)
        response <- ok(html(req).chart(ref, BoatLang(lang)))
      yield response
    case req @ GET -> Root / "vessels" / "names" =>
      for
        authed <- authedQuery(req, VesselsQuery.query)
        rows <- vessels.vessels(authed.query)
        response <- ok(VesselsResponse(rows))
      yield response
    case req @ GET -> Root / "vessels" =>
      val handler = for
        authed <- authedQuery(req, VesselQuery.query)
        rows <- vessels.load(authed.query)
        response <- respond(req)(
          json = ok(VesselHistoryResponse(rows)),
          html = ok(
            html(req).map(
              authed.user.userBoats,
              rows.headOption.flatMap(_.updates.headOption.map(_.coord))
            )
          )
        )
      yield response
      handler.recoverWith:
        case _ =>
          redirectToLogin
    case req @ GET -> Root / "stats" =>
      authedQuery(req, TracksQuery.apply).flatMap: authed =>
        comps.stats
          .stats(authed.user, authed.query, BoatLang(authed.user.language).lang)
          .flatMap: statsResponse =>
            ok(statsResponse)
    case GET -> Root / "routes" / DoubleVar(srcLat) /
        DoubleVar(srcLng) / DoubleVar(destLat) / DoubleVar(destLng) =>
      RouteRequest(srcLat, srcLng, destLat, destLng)
        .map: req =>
          F.blocking(graph.shortest(req.from, req.to))
            .flatMap: op =>
              op.map(result => ok(result))
                .recover:
                  case NoRoute(f, t)     => notFound(Errors(s"No route found from '$f' to '$t'."))
                  case UnresolvedFrom(f) => badRequest(Errors(s"Unresolvable from '$f'."))
                  case UnresolvedTo(t)   => badRequest(Errors(s"Unresolvable to '$t'."))
                  case EmptyGraph        => serverError(Errors("Graph engine not available."))
        .recover(err => badRequest(Errors(err.message)))
    case req @ POST -> Root / "cars" =>
      formAction[Polestar.Creds](req): (creds, user) =>
        comps.polestar
          .save(creds, user.id)
          .flatMap: _ =>
            seeOther(reverse.boats)
    case GET -> Root / "cars" / "conf" =>
      ok(CarsConf.default)
    case GET -> Root / "cars" / "parkings" / "capacity" =>
      comps.parking
        .capacity()
        .flatMap: json =>
          ok(json)
    case req @ GET -> Root / "cars" / "parkings" / "search" =>
      Near(req.uri.query)
        .map: query =>
          comps.parking
            .near(query)
            .flatMap: results =>
              ok(ParkingResponse(results))
        .recover: err =>
          badRequest(Errors(err.message))
    case req @ POST -> Root / "cars" / "locations" =>
      jsonAction[LocationUpdates](req): input =>
        val start = System.currentTimeMillis()
        val user = input.user
        val body = input.payload
        val lang = BoatLang(user.language)
        user.boats
          .find(_.id == body.carId)
          .map: device =>
            val deviceMeta =
              SimpleSourceMeta(user.username, device.name, device.sourceType, user.language)
            inserts
              .joinAsSource(deviceMeta)
              .flatMap: result =>
                val meta = result.track
                val count = body.updates.size
                val time = System.currentTimeMillis() - start
                log.debug(
                  s"User ${user.email} POSTs $count car updates for ${meta.boatName}, join took $time ms..."
                )
                val insertion = body.updates
                  .traverse: loc =>
                    streams.saveCarCoord(CarCoord.fromUpdate(loc, meta.track, req.userAgent))
                  .onError: t =>
                    F.delay(log.error(s"Failed to save car locations. Got '${body.asJson}'.", t))
                def pushTask(latest: JoinedTrack) =
                  val state = PushState(
                    meta,
                    SourceState.Connected,
                    latest.distance,
                    latest.duration,
                    result.isResumed,
                    body.updates.headOption.map(_.coord),
                    lang.lang.push,
                    input.receivedAt
                  )
                  pushGeocoded(state)

                for
                  inserteds <- insertion
                  _ <- inserteds.lastOption.map(i => pushTask(i.track)).getOrElse(F.pure(()))
                  duration = System.currentTimeMillis() - start
                  response <- ok(SimpleMessage(s"Saved ${inserteds.size} updates in $duration ms."))
                yield response
          .getOrElse:
            notFound(Errors(SingleError.input(s"Car not found: '${body.carId}'.")))
    case req @ GET -> Root / "sign-in" =>
      val timeNow = now()
      req.cookies
        .find(_.name == cookieNames.provider)
        .flatMap(cookie => AuthProvider.forString(cookie.content).toOption)
        .filter(_ => !req.uri.query.params.contains("reset"))
        .map: provider =>
          if provider == AuthProvider.Apple then
            val recreation = web
              .parseLongTermCookie(req.headers)
              .map: idToken =>
                auth.webSiwa
                  .recreate(idToken, timeNow)
                  .flatMap(boatJwt => appleResult(boatJwt, req))
            recreation.getOrElse(temporaryRedirect(reverse.signInFlow(provider)))
          else temporaryRedirect(reverse.signInFlow(provider))
        .getOrElse:
          ok(html(req).signIn(Lang.default))
    case req @ GET -> Root / "sign-in" / "google" =>
      startHinted(AuthProvider.Google, auth.googleFlow, req)
    case req @ GET -> Root / "sign-in" / "microsoft" =>
      val flow = if isCar(req) then auth.microsoftCarFlow else auth.microsoftBoatFlow
      startHinted(AuthProvider.Microsoft, flow, req)
    case req @ GET -> Root / "sign-in" / "apple" =>
      start(auth.appleWebFlow, AuthProvider.Apple, req)
    case req @ GET -> Root / "sign-in" / "callbacks" / "google" =>
      handleAuthCallback(auth.googleFlow, AuthProvider.Google, req)
    case req @ GET -> Root / "sign-in" / "callbacks" / "microsoft" =>
      val flow = if isCar(req) then auth.microsoftCarFlow else auth.microsoftBoatFlow
      handleAuthCallback(flow, AuthProvider.Microsoft, req)
    case req @ POST -> Root / "sign-in" / "callbacks" / "apple" =>
      handleAppleCallback(req)
    case GET -> Root / "sign-out" =>
      SeeOther(Location(reverse.index)).map(res => auth.web.clearSession(res))
    case GET -> Root / "docs" / "agent"          => docsRedirect("agent")
    case GET -> Root / "docs" / "support"        => docsRedirect("support")
    case req @ GET -> Root / "legal" / "privacy" => ok(html(req).privacyPolicy)
    case req @ GET -> Root / "files" =>
      comps.s3
        .files()
        .flatMap: objects =>
          val baseUrl = Urls.hostOnly(req) / "files"
          val latestObj = objects.maxByOption(_.lastModified())
          val latestUrl = latestObj.map(l => baseUrl / l.key())
          val urls = objects.map(obj => baseUrl / obj.key())
          ok(Json.obj("files" -> urls.asJson, "latest" -> latestUrl.asJson))(using jsonEncoder[F])
    case GET -> Root / "files" / S3KeyVar(key) =>
      if key.key.endsWith(".deb") then
        comps.s3
          .download(key)
          .flatMap: fileOpt =>
            fileOpt
              .map: file =>
                val stream = fs2.io.file.Files[F].readAll(file)
                ok(stream)
              .getOrElse:
                notFoundWith(s"File not found: '$key'.")
      else badRequest(Errors(s"Invalid key."))
    case req @ GET -> Root / ".well-known" / "apple-app-site-association" =>
      fileFromPublicResources("apple-app-site-association.json", req)
    case req @ GET -> Root / ".well-known" / "assetlinks.json" =>
      fileFromPublicResources("android-assetlinks.json", req)
    case req @ GET -> Root / TrackCanonicalVar(canonical) =>
      respond(req)(
        json = authedTrackQuery(req).flatMap: authed =>
          db.canonical(canonical, authed.user.language)
            .flatMap(ref => ok(TrackResponse(ref)))
            .handleErrorWith: t =>
              val errors = Errors(s"Not found: '$canonical'.")
              log.info(s"${errors.message}", t)
              notFound(errors)
        ,
        html = index(req)
      )

  private def updateBoat(req: Request[F], device: DeviceId) =
    formAction[PatchBoat](req): (patch, user) =>
      inserts
        .updateBoat(device, patch, user.id)
        .flatMap(row => boatResponse(req, row))

  private def socketRoutes(sockets: WebSocketBuilder2[F]): HttpRoutes[F] = HttpRoutes.of[F]:
    case req @ GET -> Root / "ws" / "updates" =>
      auth
        .authOrAnon(req.headers)
        .flatMap: user =>
          val username = user.username
          val isAnon = username == Usernames.anon
          val formatter = TimeFormatter.lang(user.language)
          BoatQuery(req.uri.query)
            .map: boatQuery =>
              if !isAnon then
                log.info(s"Viewer '$username' joined with query ${boatQuery.describe}.")
              val historicalLimits =
                if boatQuery.tracks.nonEmpty && isAnon then BoatQuery.tracks(boatQuery.tracks)
                else if isAnon then BoatQuery.empty
                else boatQuery
              val historyIO = db
                .history(user, historicalLimits)
                .map: es =>
                  // Unless a sample is specified, returns about 1000 historical points - this optimization is for charts.
                  // Also, the websocket message size must be below 1 MB for iOS. Either we sample, or slice the
                  // messages to smaller sizes. Currently, sampling is used.
                  val coordsCount = es.map(_.coords.length).sum
                  val fallbackSample = math.max(1, coordsCount / 1000)
                  val actualSample = boatQuery.sample.getOrElse(fallbackSample)
                  val sampled = es.toList.map(_.sample(actualSample))
                  val sampledCount = sampled.map(_.coords.length).sum
                  val trackIds = sampled.map(_.from.track).sorted.distinct.mkString(", ")
                  if !isAnon then
                    log.info(
                      s"Returning history of $sampledCount/$coordsCount coords for track IDs $trackIds for user ${user.username}."
                    )
                  sampled
              val simpleQuery = boatQuery.simple
              val aisTrails: F[VesselTrailsEvent] = vessels
                .load(boatQuery)
                .map: vs =>
                  val trails = vs.map: vh =>
                    val ups = vh.updates.map: up =>
                      VesselPoint(
                        up.coord,
                        up.sog,
                        up.cog,
                        up.destination,
                        up.heading,
                        up.eta,
                        formatter.timing(up.added)
                      )
                    VesselTrail(vh.mmsi, vh.name, vh.draft, ups)
                  VesselTrailsEvent(trails)
              val historyData: F[Seq[FrontEvent]] =
                if boatQuery.hasVesselFilters then aisTrails.map(e => Seq(e))
                else historyIO.map[Seq[FrontEvent]](identity)
              val historyOrNoData: F[Seq[FrontEvent]] = historyData.map: es =>
                if es.isEmpty then Seq(NoDataEvent(simpleQuery))
                else es
              val history = Stream.evalSeq(historyOrNoData)

              val updates =
                if boatQuery.hasVesselFilters then Stream.never[F]
                else streams.clientEvents(formatter)
              val eventSource = (Stream(LoadingEvent(simpleQuery)) ++ history ++ updates)
                .mergeHaltBoth(pings)
                .filter(_.isIntendedFor(user))
                .map(message => Text(message.asJson.noSpaces))
              webSocket(
                sockets,
                eventSource,
                message => F.delay(log.debug(message)),
                onClose = F.delay:
                  if !isAnon then log.info(s"Viewer '$username' left.")
              )
            .recover: errors =>
              log.warn(s"User '$username' sent a bad boat query, failing. (${errors.message})")
              badRequest(errors)
    case req @ GET -> Root / "ws" / "boats" =>
      auth
        .authBoat(req.headers)
        .flatMap: boat =>
          val lang = BoatLang(boat.language)
          val pushLang = lang.lang.push
          val now = Instant.now()
          log.info(s"Boat '${boat.boat}' by '${boat.user}' connected.")
          inserts
            .joinAsSource(boat)
            .flatMap: result =>
              val meta = result.track
              val state = PushState(
                meta,
                SourceState.Connected,
                DistanceM.zero,
                0.seconds,
                result.isResumed,
                at = None,
                lang = pushLang,
                now = now
              )
              pushGeocoded(state)
                .flatMap: _ =>
                  webSocket(
                    sockets,
                    toClients,
                    message =>
                      log.debug(s"Boat ${boat.describe} says '$message'.")
                      parse(message).fold(
                        _ =>
                          F.raiseError(
                            Exception(s"Unacceptable message from ${boat.describe}: '$message'.")
                          ),
                        parsed =>
                          val pushUpdate = db
                            .refOpt(meta.trackName, boat.language)
                            .flatMap: ref =>
                              val updatedState = PushState(
                                meta,
                                SourceState.Connected,
                                ref.map(_.distanceMeters).getOrElse(DistanceM.zero),
                                ref.map(_.duration).getOrElse(0.seconds),
                                isResumed = true,
                                at = None,
                                lang = pushLang,
                                now = Instant.now()
                              )
                              pushGeocoded(updatedState)
                          val publishEvent = streams.boatIn
                            .publish1(BoatEvent(parsed, meta, req.userAgent))
                            .map: e =>
                              e.fold(
                                err =>
                                  log.warn(
                                    s"Failed to publish '$message' by ${boat.describe}, topic closed."
                                  ),
                                identity
                              )
                          pushUpdate >> publishEvent
                      )
                    ,
                    onClose = F
                      .delay(log.info(s"Boat ${boat.describe} left."))
                      .flatMap: _ =>
                        val state = PushState(
                          meta,
                          SourceState.Disconnected,
                          DistanceM.zero,
                          0.seconds,
                          isResumed = false,
                          at = None,
                          lang = pushLang,
                          now = now
                        )
                        push
                          .push(state, geo = None)
                          .map(_ => ())
                      .handleError: err =>
                        log.warn(s"Failed to notify of disconnection of ${boat.describe}.", err)
                  ).onError: t =>
                    F.delay(log.info(s"Boat ${boat.describe} left exceptionally.", t))
                      .flatMap: _ =>
                        val state = PushState(
                          meta,
                          SourceState.Disconnected,
                          DistanceM.zero,
                          0.seconds,
                          isResumed = false,
                          at = None,
                          lang = pushLang,
                          now = now
                        )
                        push
                          .push(state, geo = None)
                          .map(_ => ())
                      .handleError: err =>
                        log.info(s"Failed to handler error of ${boat.describe}.", err)

  def routes(sockets: WebSocketBuilder2[F]): HttpRoutes[F] =
    normalRoutes.combineK(socketRoutes(sockets))

  private def pushGeocoded(state: PushState) =
    val reverseGeo = state.at
      .map: coord =>
        comps.mapbox
          .reverseGeocode(coord)
          .handleErrorWith: e =>
            F.delay(log.error(s"Geo lookup of $coord failed.", e)) >> F.pure(None)
      .getOrElse:
        F.pure(None)
    reverseGeo.flatMap: geo =>
      push
        .push(state, geo)
        .void
        .handleErrorWith: t =>
          F.delay(
            log.error(
              s"Failed to push all device notifications for '${state.device.deviceName}'.",
              t
            )
          )

  private def webSocket(
    sockets: WebSocketBuilder2[F],
    toClient: Stream[F, WebSocketFrame],
    onMessage: String => F[Unit],
    onClose: F[Unit]
  ): F[Response[F]] =
    val fromClient: Pipe[F, WebSocketFrame, Unit] = _.evalMap:
      case Text(message, _) =>
        log.debug(s"Message $message")
        onMessage(message)
      case f =>
        F.delay(log.debug(s"Unknown WebSocket frame: $f"))
    sockets.withOnClose(onClose).build(toClient, fromClient)

  private def index(req: Request[F]): F[Response[F]] =
    auth
      .optionalWebAuth(req)
      .flatMap: result =>
        result
          .map: userRequest =>
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
            ok(html(req).map(maybeBoat.getOrElse(UserBoats.anon))).map: res =>
              val cookied = res.addCookie(tokenCookie).addCookie(languageCookie)
              auth.saveSettings(SettingsPayload(u, lang, authorizedBoats), cookied, isSecure)
          .recover(_ => redirectToLogin)

  private def trackAction[T: Decoder](req: Request[F])(
    code: (T, UserInfo) => F[JoinedTrack]
  )(using EntityDecoder[F, T]) =
    formAction[T](req): (t, user) =>
      code(t, user).flatMap: track =>
        val formatter = TimeFormatter.lang(user.language)
        ok(TrackResponse(track.strip(formatter)))

  given Readable[BoatName] = Readable.string.emap(s => BoatName.build(CIString(s.trim)))

  private def boatResponse(req: Request[F], row: SourceRow) =
    respond(req)(
      json = ok(BoatResponse(row.toBoat)),
      html = SeeOther(Location(reverse.boats))
    )

  private def formAction[T: Decoder](req: Request[F])(
    code: (T, UserInfo) => F[Response[F]]
  )(using decoder: EntityDecoder[F, T]): F[Response[F]] =
    auth
      .profile(req)
      .flatMap: user =>
        val isForm = req.headers
          .get[`Content-Type`]
          .exists(_.mediaType == MediaType.application.`x-www-form-urlencoded`)
        val decoded =
          if isForm then req.as[T]
          else req.decodeJson[T]
        decoded
          .flatMap(t => code(t, user))
          .handleErrorWith: err =>
            log.error(s"Form failure. $err")
            badRequest(Errors(SingleError.input("Invalid form input.")))

  private def jsonAction[T: Decoder](req: Request[F])(
    code: JsonRequest[F, T, UserInfo] => F[Response[F]]
  ): F[Response[F]] =
    auth
      .profile(req)
      .flatMap: user =>
        req.decodeJson[T].flatMap(t => code(JsonRequest(user, t, req, Instant.now())))

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
    makeAuth(req.headers).flatMap: user =>
      query(req.uri.query).fold(
        err => F.raiseError(InvalidRequest(req, err)),
        q => F.pure(AnyBoatRequest(user, q, req.headers))
      )

  private def start(validator: FlowStart[F], provider: AuthProvider, req: Request[F]) =
    validator
      .start(Urls.hostOnly(req) / reverse.signInCallback(provider).renderString, Map.empty)
      .flatMap(s => startLoginFlow(s, req.isSecured))

  private def startHinted(
    provider: AuthProvider,
    validator: LoginHint[F],
    req: Request[F]
  ): F[Response[F]] = F
    .delay:
      val redirectUrl = Urls.hostOnly(req) / reverse.signInCallback(provider).renderString
      val lastIdCookie = req.cookies.find(_.name == cookieNames.lastId)
      val promptValue = req.cookies
        .find(_.name == cookieNames.prompt)
        .map(_.content)
        .orElse(Option(SelectAccount).filter(_ => lastIdCookie.isEmpty))
      val extra = promptValue.map(c => Map(PromptKey -> c)).getOrElse(Map.empty)
      val maybeEmail = lastIdCookie.map(_.content).filter(_ => extra.isEmpty)
      maybeEmail.foreach: hint =>
        log.info(s"Starting OAuth flow with $provider using login hint '$hint'...")
      promptValue.foreach: prompt =>
        log.info(s"Starting OAuth flow with $provider using prompt '$prompt'...")
      (redirectUrl, maybeEmail, extra)
    .flatMap:
      case (redirectUrl, maybeEmail, extra) =>
        validator
          .startHinted(redirectUrl, maybeEmail, extra)
          .flatMap: s =>
            startLoginFlow(s, req.isSecured)

  private def startLoginFlow(s: Start, isSecure: Boolean): F[Response[F]] = F
    .delay:
      val state = randomString()
      val encodedParams = (s.params ++ Map(OAuthKeys.State -> state)).map:
        case (k, v) =>
          k -> com.malliina.web.Utils.urlEncode(v)
      val url = s.authorizationEndpoint.append(s"?${stringify(encodedParams)}")
      log.info(s"Redirecting to '$url' with state '$state'...")
      val sessionParams: Seq[(String, String)] = Seq(State -> state) ++ s.nonce
        .map(n => Seq(Nonce -> n))
        .getOrElse(Nil)
      (url, sessionParams)
    .flatMap:
      case (url, sessionParams) =>
        SeeOther(Location(Uri.unsafeFromString(url.url))).map: res =>
          val session = sessionParams.toMap.asJson
          auth.web
            .withSession(session, isSecure, res)
            .putHeaders(noCache)

  private def handleAuthCallback(
    flow: DiscoveringAuthFlow[F, Email],
    provider: AuthProvider,
    req: Request[F]
  ) = handleCallback(
    req,
    provider,
    cb => flow.validateCallback(cb).map(e => e.flatMap(v => flow.parse(v)))
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
    validate(cb).flatMap: e =>
      e.fold(
        err => unauthorized(Errors(err.message.message)),
        email => userResult(email, provider, req)
      )

  private def handleAppleCallback(req: Request[F]): F[Response[F]] =
    req
      .attemptAs[AppleResponse]
      .foldF(
        failure => unauthorized(Errors(failure.message)),
        response =>
          val session =
            web.authState[Map[String, String]](req.headers).toOption.getOrElse(Map.empty)
          val actualState = response.state
          val sessionState = session.get(State)
          if sessionState.contains(actualState) then
            val redirectUrl =
              Urls.hostOnly(req) / reverse.signInCallback(AuthProvider.Apple).renderString
            auth.webSiwa
              .registerWeb(response.code, now(), redirectUrl)
              .flatMap: boatJwt =>
                appleResult(boatJwt, req)
          else
            val detailed =
              sessionState.fold(s"Got '$actualState' but found nothing to compare to."): expected =>
                s"Got '$actualState' but expected '$expected'."
            log.error(s"Authentication failed, state mismatch. $detailed $req")
            unauthorized(Errors("State mismatch."))
      )

  private def appleResult(boatJwt: BoatJwt, req: Request[F]) =
    userResult(boatJwt.email, AuthProvider.Apple, req).map: res =>
      res.addCookie(web.longTermCookie(boatJwt.idToken))

  private def userResult(
    email: Email,
    provider: AuthProvider,
    req: Request[F]
  ): F[Response[F]] =
    val returnUri: Uri = req.cookies
      .find(_.name == cookieNames.returnUri)
      .flatMap(c => Uri.fromString(c.content).toOption)
      .getOrElse(reverse.index)
    SeeOther(Location(returnUri)).map: r =>
      web.withAppUser(UserPayload.email(email), Urls.isSecure(req), provider, r)

  def stringify(map: Map[String, String]): String =
    map.map((key, value) => s"$key=$value").mkString("&")

  private def unauthorized(@unused errors: Errors) = redirectToLogin
  private def redirectToLogin: F[Response[F]] = SeeOther(Location(reverse.signIn))
  private def now() = Instant.now()
