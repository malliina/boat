package com.malliina.boat.http4s

import cats.Show
import cats.syntax.show.toShow
import com.malliina.boat.auth.AuthProvider
import com.malliina.boat.http.CarQuery
import com.malliina.boat.*
import org.http4s.Uri
import org.http4s.Uri.Path.SegmentEncoder
import org.http4s.implicits.*

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object Reverse:
  private def longEncoder[T, C <: ShowableLong[T]](comp: C): SegmentEncoder[T] =
    SegmentEncoder.longSegmentEncoder.contramap[T](t => comp.write(t))

  given showSegmentEncoder[T: Show]: SegmentEncoder[T] =
    SegmentEncoder.stringSegmentEncoder.contramap[T](t => t.show)

  given SegmentEncoder[DeviceId] = longEncoder(DeviceId)
  given SegmentEncoder[TrackId] = longEncoder(TrackId)

  val root = uri"/"
  val index = root
  val pingAuth = uri"/pingAuth"
  val health = uri"/health"
  val conf = uri"/conf"
  val me = uri"/users/me"
  val usersBoats = uri"/users/boats"
  val usersBoatsAnswers = uri"/users/boats/answers"
  def usersBoat(id: DeviceId) = uri"/users/boats" / id
//  def inviteByEmail(id: DeviceId) = usersBoat(id)
  def invites = uri"/invites"
  def revoke = uri"/invites/revoke"
  def invitesRespond = uri"/invites/respond"
  val notifications = uri"/users/notifications"
  val notificationsDisable = uri"/users/notifications/disable"
  val boats = uri"/boats"
  val createBoat = boats
  def boat(id: DeviceId) = uri"/boats" / id
  def boatDelete(id: DeviceId) = uri"/boats" / id / "delete"
  def boatEdit(id: DeviceId) = uri"/boats" / id / "edit"
  val history = uri"/history"
  val tracks = uri"/tracks"
  def track(id: TrackId) = tracks / id
  def modifyTitle(name: TrackName) = tracks / name
  def updateComments(id: TrackId) = tracks / id
  def trackFull(name: TrackName) = tracks / name / "full"
  def trackChart(name: TrackName) = tracks / name / "chart"
  val stats = uri"/stats"
  def shortest(srclat: Double, srclng: Double, destlat: Double, destlng: Double) =
    uri"/routes" / srclat / srclng / destlat / destlng
  object ws:
    val updates = uri"/ws/updates"
    val boats = uri"/ws/boats"
    val devices = uri"/ws/devices"
  val signIn = uri"/sign-in"
  def signInFlow(provider: AuthProvider) = signIn / provider.name
  def signInCallback(provider: AuthProvider) = signIn / "callbacks" / provider.name
  val signOut = uri"/sign-out"
  val docsAgent = uri"/docs/agent"
  val docsSupport = uri"/docs/support"
  val legal = uri"/legal/privacy"
  val files = uri"/files"
  val postCars = uri"/cars/locations"
  val historyCars = uri"/cars/history"
  def history(q: CarQuery) =
    val t = q.timeRange
    val iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    val params =
      t.from.map(i => Seq(Timings.From -> iso.format(i.atOffset(ZoneOffset.UTC)))).getOrElse(Nil) ++
        t.to.map(i => Seq(Timings.To -> iso.format(i.atOffset(ZoneOffset.UTC)))).getOrElse(Nil)
    uri"/".withQueryParams(params.toMap)
  def file(id: String) = uri"/files" / id
  def canonical(id: TrackCanonical) = uri"/" / id
