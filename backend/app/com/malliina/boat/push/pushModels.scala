package com.malliina.boat.push

import com.malliina.boat.BoatName
import com.malliina.boat.http.Named
import com.malliina.push.apns.{APNSError, APNSIdentifier, APNSToken}
import com.malliina.values.{ErrorMessage, ValidatingCompanion}
import play.api.libs.json.Json

sealed abstract class BoatState(val name: String) extends Named

object BoatState extends ValidatingCompanion[String, BoatState] {
  val Key = "state"
  val all = Seq(Connected, Disconnected)

  override def build(input: String): Either[ErrorMessage, BoatState] =
    all.find(_.name == input).toRight(ErrorMessage(s"Unknown boat state: '$input"))

  override def write(t: BoatState): String = t.name

  case object Connected extends BoatState("connected")

  case object Disconnected extends BoatState("disconnected")

}

case class BoatNotification(boatName: BoatName, state: BoatState) {
  def message = s"$boatName $state"
}

object BoatNotification {
  implicit val json = Json.format[BoatNotification]
}

case class APNSHttpResult(token: APNSToken, id: Option[APNSIdentifier], error: Option[APNSError])

object APNSHttpResult {
  implicit val json = Json.format[APNSHttpResult]
}
