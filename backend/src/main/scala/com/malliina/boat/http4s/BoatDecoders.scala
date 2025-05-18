package com.malliina.boat.http4s

import cats.effect.Concurrent
import com.comcast.ip4s.{Host, Port}
import com.malliina.boat.http.{InvitePayload, InviteResponse, RevokeAccess}
import com.malliina.boat.{AddSource, BoatName, BoatNames, ChangeBoatName, ChangeComments, ChangeTrackTitle, DeviceId, Forms, GPSInfo, Passwords, PatchBoat, Readables, SourceType, TrackComments, TrackTitle, Usernames}
import com.malliina.http4s.{FormDecoders, FormReadableT}
import com.malliina.polestar.Polestar
import com.malliina.values.{Email, Password, UserId, Username}

trait BoatDecoders[F[_]: Concurrent] extends FormDecoders[F]:
  import Readables.given

  private val reader = FormReadableT.reader

  given FormReadableT[InvitePayload] = reader.emap: form =>
    for
      boat <- form.read[DeviceId](Forms.Boat)
      email <- form.read[Email](Forms.Email)
    yield InvitePayload(boat, email)

  given FormReadableT[InviteResponse] = reader.emap: form =>
    for
      boat <- form.read[DeviceId](Forms.Boat)
      accept <- form.read[Boolean](Forms.Accept)
    yield InviteResponse(boat, accept)

  given FormReadableT[RevokeAccess] = reader.emap: form =>
    for
      boat <- form.read[DeviceId](Forms.Boat)
      user <- form.read[UserId](Forms.User)
    yield RevokeAccess(boat, user)

  given FormReadableT[AddSource] = reader.emap: form =>
    for
      name <- form.read[BoatName](BoatNames.Key)
      sourceType <- form.read[SourceType](SourceType.Key)
    yield AddSource(name, sourceType)

  given FormReadableT[ChangeBoatName] = reader.emap: form =>
    form.read[BoatName](BoatNames.Key).map(n => ChangeBoatName(n))

  given FormReadableT[GPSInfo] = reader.emap: form =>
    for
      ip <- form.read[Host](GPSInfo.IpKey)
      port <- form.read[Port](GPSInfo.PortKey)
    yield GPSInfo(ip, port)

  given FormReadableT[ChangeTrackTitle] = reader.emap: form =>
    form.read[TrackTitle](TrackTitle.Key).map(ChangeTrackTitle.apply)

  given FormReadableT[ChangeComments] = reader.emap: form =>
    form.read[String](TrackComments.Key).map(ChangeComments.apply)

  given FormReadableT[PatchBoat] =
    FormReadableT[ChangeBoatName]
      .map(PatchBoat.ChangeName.apply)
      .or(FormReadableT[GPSInfo].map(PatchBoat.UpdateGps.apply))

  given FormReadableT[Polestar.Creds] = reader.emap: form =>
    for
      user <- form.read[Username](Usernames.Key)
      pass <- form.read[Password](Passwords.Key)
    yield Polestar.Creds(user, pass)
