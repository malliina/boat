package controllers

import java.time.{LocalDate, LocalTime}

import com.malliina.boat.db.NewUser
import com.malliina.boat.parsing.FullCoord
import com.malliina.boat.{BoatNames, BoatUser, Coord, TrackNames, TrackSummaries, Tracks, UserToken}
import com.malliina.measure.{DistanceInt, SpeedInt, TemperatureInt}
import com.malliina.values.Username
import play.api.http.HeaderNames.{ACCEPT, AUTHORIZATION}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import tests.{TestAppSuite, TestEmailAuth}

class BoatControllerTests extends TestAppSuite {
  test("tracks versioning") {
    val user = Username("test")
    val service = components.tracks
    val init = for {
      _ <- components.users.addUser(NewUser(user, Option(TestEmailAuth.testEmail), UserToken.random(), enabled = true))
      track <- service.join(BoatUser(TrackNames.random(), BoatNames.random(), user))
      coord = FullCoord(Coord(60, 24), LocalTime.now(), LocalDate.now(), 10.knots, 10.celsius, 10.meters, 0.meters, track.short)
      p <- service.saveCoords(coord)
    } yield p
    await(init)
    val response1 = tracksRequest(ContentVersions.Version1)
    val summaries = contentAsJson(response1).as[TrackSummaries]
    assert(summaries.tracks.length === 1)
    val response2 = tracksRequest(ContentVersions.Version2)
    val tracks = contentAsJson(response2).as[Tracks]
    assert(tracks.tracks.length === 1)
  }

  def tracksRequest(accept: String) = {
    val authHeader = s"Bearer ${TestEmailAuth.testToken}"
    val req = FakeRequest(routes.BoatController.tracks())
      .withHeaders(AUTHORIZATION -> authHeader, ACCEPT -> accept)
    route(app, req).get
  }
}