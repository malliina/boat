package com.malliina.boat.http

import com.malliina.boat.{DeviceId, InviteState}
import com.malliina.values.{Email, StringEnumCompanion, UserId}
import play.api.libs.json.Json

sealed abstract class AccessOperation(val name: String)

object AccessOperation extends StringEnumCompanion[AccessOperation] {
  case object Grant extends AccessOperation("grant")
  case object Revoke extends AccessOperation("revoke")

  override def all = Seq(Grant, Revoke)
  override def write(t: AccessOperation) = t.name
}

case class BoatInvite(email: Email)

object BoatInvite {
  implicit val json = Json.format[BoatInvite]
}

case class InvitePayload(boat: DeviceId, email: Email) {
  def byUser(user: UserId) = InviteInfo(boat, email, user)
}

object InvitePayload {
  implicit val json = Json.format[InvitePayload]
}

case class InviteInfo(boat: DeviceId, email: Email, principal: UserId)

case class BoatAccess(boat: DeviceId, user: UserId, operation: AccessOperation)

object BoatAccess {
  implicit val json = Json.format[BoatAccess]
}

case class InviteAnswer(boat: DeviceId, state: InviteState)

object InviteAnswer {
  implicit val json = Json.format[InviteAnswer]
}

case class AccessResult(existed: Boolean)

object AccessResult {
  implicit val json = Json.format[AccessResult]
}
