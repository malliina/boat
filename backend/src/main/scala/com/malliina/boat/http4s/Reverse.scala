package com.malliina.boat.http4s

import com.malliina.boat.{DeviceId, TrackCanonical, TrackId, TrackName}
import org.http4s.Uri
import org.http4s.implicits._

object Reverse {
  val root = uri"/"
  val index = root
  val pingAuth = uri"/pingAuth"
  val health = uri"/health"
  val conf = uri"/conf"
  val me = uri"/users/me"
  val usersBoats = uri"/users/boats"
  val usersBoatsAnswers = uri"/users/boats/answers"
  def usersBoat(id: DeviceId) = Uri.unsafeFromString(s"/users/boats/$id")
//  def inviteByEmail(id: DeviceId) = usersBoat(id)
  def invites = uri"/invites"
  val notifications = uri"/users/notifications"
  val notificationsDisable = uri"/users/notifications/disable"
  val boats = uri"/boats"
  val createBoat = boats
  def boat(id: DeviceId) = Uri.unsafeFromString(s"/boats/$id")
  def boatDelete(id: DeviceId) = Uri.unsafeFromString(s"/boats/$id/delete")
  val history = uri"/history"
  val tracks = uri"/tracks"
  def track(id: TrackId) = Uri.unsafeFromString(s"/tracks/$id")
  def modifyTitle(name: TrackName) = Uri.unsafeFromString(s"/tracks/$name")
  def updateComments(id: TrackId) = Uri.unsafeFromString(s"/tracks/$id")
  def trackFull(name: TrackName) = Uri.unsafeFromString(s"/tracks/$name/full")
  def trackChart(name: TrackName) = Uri.unsafeFromString(s"/tracks/$name/chart")
  val stats = uri"/stats"
  def shortest(srclat: Double, srclng: Double, destlat: Double, destlng: Double) =
    Uri.unsafeFromString(s"/routes/$srclat/$srclng/$destlat/$destlng")
  object ws {
    val updates = uri"/ws/updates"
    val boats = uri"/ws/boats"
    val devices = uri"/ws/devices"
  }
  val google = uri"/sign-in/google"
  val googleCallback = uri"/sign-in/callbacks/google"
  val signOut = uri"/sign-out"
  val docsAgent = uri"/docs/agent"
  val docsSupport = uri"/docs/support"
  val legal = uri"/legal/privacy"
  val files = uri"/files"
  def file(id: String) = Uri.unsafeFromString(s"/files/$id")
  def canonical(id: TrackCanonical) = Uri.unsafeFromString(s"/$id")
}
