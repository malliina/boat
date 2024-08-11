package com.malliina.boat.http

import com.malliina.boat.{DeviceId, InviteState}
import com.malliina.values.{Email, StringEnumCompanion, UserId}
import io.circe.*

sealed abstract class AccessOperation(val name: String)

object AccessOperation extends StringEnumCompanion[AccessOperation]:
  case object Grant extends AccessOperation("grant")
  case object Revoke extends AccessOperation("revoke")

  override def all = Seq(Grant, Revoke)
  override def write(t: AccessOperation) = t.name

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

sealed trait InviteResult

object InviteResult:
  case class UnknownEmail(email: Email) extends InviteResult
  case class Invited(user: UserId, to: DeviceId) extends InviteResult
  case class AlreadyInvited(user: UserId, to: DeviceId) extends InviteResult
