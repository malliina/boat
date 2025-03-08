package com.malliina.boat.http

import com.malliina.boat.{DeviceId, InviteState}
import com.malliina.values.{Email, StringEnumCompanion, UserId}
import io.circe.*

enum AccessOperation(val name: String):
  case Grant extends AccessOperation("grant")
  case Revoke extends AccessOperation("revoke")

object AccessOperation extends StringEnumCompanion[AccessOperation]:
  override def all: Seq[AccessOperation] = Seq(Grant, Revoke)
  override def write(t: AccessOperation): String = t.name

case class BoatInvite(email: Email) derives Codec.AsObject

case class RevokeAccess(to: DeviceId, from: UserId) derives Codec.AsObject

case class InviteResponse(to: DeviceId, accept: Boolean) derives Codec.AsObject

case class InvitePayload(boat: DeviceId, email: Email) derives Codec.AsObject:
  def byUser(user: UserId) = InviteInfo(boat, email, user)

case class InviteInfo(boat: DeviceId, email: Email, principal: UserId)

case class BoatAccess(boat: DeviceId, user: UserId, operation: AccessOperation)
  derives Codec.AsObject

case class InviteAnswer(boat: DeviceId, state: InviteState) derives Codec.AsObject

case class AccessResult(existed: Boolean) derives Codec.AsObject

case class EmailUser(user: UserId, email: Email)

enum InviteResult:
  case UnknownEmail(email: Email) extends InviteResult
  case Invited(user: UserId, to: DeviceId) extends InviteResult
  case AlreadyInvited(user: UserId, to: DeviceId) extends InviteResult
