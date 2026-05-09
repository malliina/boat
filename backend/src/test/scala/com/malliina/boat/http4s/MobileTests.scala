package com.malliina.boat.http4s

import cats.effect.IO
import cats.implicits.toShow
import com.malliina.boat.*
import com.malliina.boat.db.NewUser
import com.malliina.geo.{Latitude, Longitude}
import com.malliina.http.CSRFConf
import com.malliina.measure.*
import com.malliina.values.Literals.user
import com.malliina.values.{Username, degrees, lat, lng}

import java.time.{OffsetDateTime, ZoneOffset}

class MobileTests extends BoatServerFunSuite:
  val ts = OffsetDateTime.of(2023, 4, 2, 10, 4, 3, 0, ZoneOffset.UTC)

  srv.test("POST mobile device, then POST mobile locations"): s =>
    val user: Username = user"mobile@example.com"
    val src = AddSource(None, SourceType.Mobile)
    val ts = OffsetDateTime.of(2023, 4, 2, 10, 4, 3, 0, ZoneOffset.UTC)
    val loc1 = testLoc(26.lng, 61.lat, ts)
    val loc2 = testLoc(25.8.lng, 61.1.lat, ts.plusMinutes(1))
    val service = s.server.app
    for
      _ <- service.userMgmt.deleteUser(user)
      _ <- service.userMgmt.addUser(
        NewUser(
          user,
          Option(TestEmailAuth[IO].testMobileEmail),
          UserToken.random(),
          enabled = true
        )
      )
      creation <- http.postAs[AddSource, BoatResponse](
        s.baseHttpUrl / Reverse.boats,
        src,
        headersStr(TestEmailAuth.mobileToken, s.csrf)
      )
      upd1 <- http.postAs[SourceLocations, SimpleMessage](
        s.baseHttpUrl / Reverse.locations,
        SourceLocations(List(loc1)),
        boatHeaders(creation.boat.token, s.csrf)
      )
      upd2 <- http.postAs[SourceLocations, SimpleMessage](
        s.baseHttpUrl / Reverse.locations,
        SourceLocations(List(loc2)),
        boatHeaders(creation.boat.token, s.csrf)
      )
    yield
      assert(upd1.message.startsWith("Handled"))
      assert(upd2.message.startsWith("Handled"))

  protected def boatHeaders(token: BoatToken, csrf: CSRFConf) =
    val bh = Map(BoatHeaders.BoatTokenHeader -> token.show)
    (baseHeaders(csrf) ++ bh).map((k, v) => k.toString -> v)

  def testLoc(lng: Longitude, lat: Latitude, time: OffsetDateTime) = LocationUpdate(
    lng,
    lat,
    Option(1.meters),
    Option(5.meters),
    Option(128f.degrees),
    None,
    Option(5.kmh),
    None,
    None,
    None,
    Option(24.5.celsius),
    None,
    time
  )
