package com.malliina.boat.http4s

import cats.effect.IO
import com.malliina.boat.db.NewUser
import com.malliina.boat.http.ContentVersions
import com.malliina.boat.http4s.JsonInstances.jsonBody
import com.malliina.boat.parsing.{BoatStats, FullCoord}
import com.malliina.boat.{BoatNames, BoatUser, Coord, TrackNames, TrackSummaries, Tracks, UserToken}
import com.malliina.measure.{DistanceIntM, SpeedIntM, TemperatureInt}
import com.malliina.values.Username
import org.http4s.*
import tests.{Http4sSuite, MUnitSuite, TestEmailAuth}

import java.time.{LocalDate, LocalTime}

class ServiceTests extends MUnitSuite with Http4sSuite:
  // Ignored because I don't know how to create access an HttpApp for testing purposes with WebSocketBuilder2
  test("tracks endpoint supports versioning based on Accept header".ignore) {
    val comps = app()
    val service = comps.service
    val user = Username("test")
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
    val response1 = tracksRequest(ContentVersions.Version1)
    implicit val tsBody: EntityDecoder[IO, TrackSummaries] = jsonBody[IO, TrackSummaries]
    val summaries = response1.flatMap(_.as[TrackSummaries]).unsafeRunSync()
    assertEquals(summaries.tracks.length, 1)
    val response2 = tracksRequest(ContentVersions.Version2)
    implicit val tBody: EntityDecoder[IO, Tracks] = jsonBody[IO, Tracks]
    val tracks = response2.flatMap(_.as[Tracks]).unsafeRunSync()
    assertEquals(tracks.tracks.length, 1)
  }

  def tracksRequest(accept: MediaType): IO[Response[IO]] = ???
//    routes.run(
//      Request(
//        uri = uri"/tracks",
//        headers = Headers(
//          Authorization(Credentials.Token(AuthScheme.Basic, TestEmailAuth.testToken)),
//          Accept(accept)
//        )
//      )
//    )
