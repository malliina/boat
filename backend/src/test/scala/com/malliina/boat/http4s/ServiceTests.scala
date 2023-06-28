package com.malliina.boat.http4s

import cats.effect.IO
import com.malliina.boat.db.NewUser
import com.malliina.boat.http.ContentVersions
import com.malliina.boat.http4s.JsonInstances.jsonBody
import com.malliina.boat.parsing.{BoatStats, FullCoord}
import com.malliina.boat.{BoatName, BoatNames, BoatResponse, BoatUser, Coord, DeviceId, TrackNames, TrackSummaries, Tracks, UserToken}
import com.malliina.measure.{DistanceIntM, SpeedIntM, TemperatureInt}
import com.malliina.values.Username
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.client.*
import org.http4s.client.dsl.io.*
import org.http4s.headers.{Accept, Authorization}
import org.http4s.implicits.{mediaType, uri}
import tests.{Http4sSuite, MUnitSuite, TestEmailAuth}

import java.time.{LocalDate, LocalTime}

class ServiceTests extends MUnitSuite with Http4sSuite:
  test("can change name of boat") {
    val comps = app()
    val service = comps.service
    val user = Username("test@example.com")
    val userEmail = TestEmailAuth[IO].testEmail
    val newName = BoatNames.random()

    def changeName(of: DeviceId, to: BoatName) =
      val req = Method.PATCH(
        Json.obj("boatName" -> to.asJson),
        uri"/boats".addSegment(of.id),
        headers = Headers(
          Authorization(Credentials.Token(AuthScheme.Basic, TestEmailAuth.testToken.token)),
          Accept(mediaType"application/json")
        )
      )
      service.normalRoutes.orNotFound.run(req)
    for
      _ <- service.userMgmt.deleteUser(user)
      _ <- service.userMgmt.register(userEmail)
      user <- service.userMgmt.userInfo(userEmail)
      res <- changeName(user.boats.head.id, newName)
      _ = assertEquals(res.status, Status.Ok)
      res <- res.as[BoatResponse]
    yield assert(res.boat.name == newName)
  }

  test("tracks endpoint supports versioning based on Accept header") {
    val comps = app()
    val service = comps.service
    val user = Username("test@example.com")
    val inserts = service.inserts

    val init = for
      _ <- service.userMgmt.deleteUser(user)
      _ <- service.userMgmt.addUser(
        NewUser(user, Option(TestEmailAuth[IO].testEmail), UserToken.random(), enabled = true)
      )
      track <- inserts.joinAsSource(BoatUser(TrackNames.random(), BoatNames.random(), user))
      coord = FullCoord(
        Coord.buildOrFail(60, 24),
        LocalTime.now(),
        LocalDate.now(),
        10.knots,
        BoatStats(
          10.celsius,
          10.meters,
          0.meters
        ),
        track.track.short
      )
      p <- inserts.saveCoords(coord)
    yield p
    init.unsafeRunSync()
    val response1 = tracksRequest(ContentVersions.Version1, service.normalRoutes)
    implicit val tsBody: EntityDecoder[IO, TrackSummaries] = jsonBody[IO, TrackSummaries]
    val summaries = response1.flatMap(_.as[TrackSummaries]).unsafeRunSync()
    assertEquals(summaries.tracks.length, 1)
    val response2 = tracksRequest(ContentVersions.Version2, service.normalRoutes)
    implicit val tBody: EntityDecoder[IO, Tracks] = jsonBody[IO, Tracks]
    val tracks = response2.flatMap(_.as[Tracks]).unsafeRunSync()
    assertEquals(tracks.tracks.length, 1)
  }

  def tracksRequest(accept: MediaType, routes: HttpRoutes[IO]): IO[Response[IO]] =
    routes.orNotFound.run(
      Request(
        uri = uri"/tracks",
        headers = Headers(
          Authorization(Credentials.Token(AuthScheme.Basic, TestEmailAuth.testToken.token)),
          Accept(accept)
        )
      )
    )
