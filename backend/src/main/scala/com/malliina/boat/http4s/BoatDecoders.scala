package com.malliina.boat.http4s

import cats.effect.Concurrent
import com.comcast.ip4s.{Host, Port}
import com.malliina.boat.http.{InvitePayload, InviteResponse, RevokeAccess}
import com.malliina.boat.{AddSource, BoatName, BoatNames, ChangeBoatName, ChangeComments, ChangeTrackTitle, DeviceId, Forms, GPSInfo, PatchBoat, Readables, SourceType, TrackComments, TrackTitle}
import com.malliina.http.Errors
import com.malliina.http4s.FormReadableT
import com.malliina.values.{Email, UserId}
import org.http4s.{DecodeResult, EntityDecoder, MalformedMessageBodyFailure, UrlForm}

trait BoatDecoders[F[_]: Concurrent]:
  import Readables.given

  given [T](using reader: FormReadableT[T]): EntityDecoder[F, T] =
    EntityDecoder[F, UrlForm].flatMapR: form =>
      toDecodeResult(reader.read(form))

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

  private def toDecodeResult[T](e: Either[Errors, T]): DecodeResult[F, T] = e.fold(
    errors => DecodeResult.failureT(MalformedMessageBodyFailure(errors.message.message)),
    ok => DecodeResult.successT(ok)
  )
