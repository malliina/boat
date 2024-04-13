package com.malliina.boat

import cats.Show

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object Constants extends Constants

trait Constants extends CookieNames with BoatHeaders:
  val BoatTokenQuery = "token"
  val DefaultSample = 4
  val MaxTimeBetweenCarUpdates: FiniteDuration = 60.seconds
  val StyleIdOld = "ck8lhls0r0obm1ilkvglk0ulr"
  val StyleId = "clb7q3k8x000p14tirbrnh1em"

object BoatHeaders extends BoatHeaders

trait BoatHeaders:
  val BoatNameHeader = "X-Boat"
  val BoatTokenHeader = "X-Token"
  val TrackNameHeader = "X-Track"

object CookieNames extends CookieNames

trait CookieNames:
  val LanguageName = "lang"
  val TokenCookieName = "mapboxToken"

trait DatePickerKeys:
  val DatesContainer = "dates-container gx-0"
  val FromTimePickerId = "from-time-picker"
  val ToTimePickerId = "to-time-picker"
  val ShortcutsId = "time-shortcuts-select"
  val LoadingSpinnerId = "loading-spinner"

trait Named:
  def name: String

trait NamedCompanion[T <: Named]:
  def all: Seq[T]
  given Show[T] = s => s.name
  def fromString(s: String): Option[T] =
    all.toList.find(_.name == s)

case class TrackShortcut(latest: Int) extends Named:
  def name = s"${TrackShortcut.prefix}$latest"

object TrackShortcut:
  val prefix = "latest-"
  def fromString(s: String): Either[String, TrackShortcut] =
    if s.startsWith(prefix) then
      s.drop(prefix.length).toIntOption.map(apply).toRight(s"Invalid value: '$s'.")
    else Left(s"Must start with '$prefix'.")

  given Show[TrackShortcut] = _.name

enum Shortcut(val name: String) extends Named:
  case Last30min extends Shortcut("last30min")
  case Last2h extends Shortcut("last2h")
  case Last12h extends Shortcut("last12h")
  case Last24h extends Shortcut("last24h")
  case Last48h extends Shortcut("last48h")

object Shortcut extends NamedCompanion[Shortcut]:
  override val all: Seq[Shortcut] = values.toList

trait FilterKeys:
  val Newest = "newest"
  val SampleKey = "sample"
  val TracksLimit = "tracksLimit"

object FrontKeys extends FrontKeys

trait FrontKeys
  extends BodyClasses
  with ListKeys
  with NavKeys
  with AboutKeys
  with DatePickerKeys
  with FilterKeys:
  val ChartsId = "charts"
  val Close = "close"
  val Enabled = "enabled"
  val FullLinkId = "full-list-link"
  val HistoryLinkId = "history-link"
  val GraphLinkId = "graph-link"
  val Hidden = "hidden"
  val MapId = "map"
  val Modal = "modal"
  val ModalId = "modal"
  val PersonLink = "person-link"
  val Routes = "routes"
  val RoutesContainer = "routes-container"

  val DevicePrefix = "device"
  val TrophyPrefix = "trophy"

  val Invisible = "invisible"
  val Visible = "visible"

  val Center = "center"
  val Lng = "lng"
  val Lat = "lat"

trait AboutKeys:
  val LanguageRadios = "language-radios"

trait NavKeys:
  val DropdownContentId = "dropdown-content"
  val DropdownLinkId = "dropdown-link"
  val BoatDropdownId = "dropdown-link-boat"
  val BoatDropdownContentId = "dropdown-link-boat-content"

  val DeviceLinkClass = "device-link"

  val DistanceId = "distance"
  val ConsumptionId = "consumption"
  val DurationId = "duration"
  val TitleId = "text-title"
  val TopSpeedId = "top-speed"
  val WaterTempId = "water-temp"

  val Question = "question-link"
  val QuestionNav = "question-nav"

  val RouteLength = "route-length"
  val RouteText = "route-text"

trait ListKeys:
  val CancelEditCommentsId = "comments-cancel"
  val EditCommentsFormId = "comments-form"
  val EditCommentsId = "comments-edit"
  val CommentsInputId = "comments-value"
  val CommentsTitleId = "comments-title"
  val CommentsRow = "comments-row"
  val FormParent = "form-parent"
  val InvitesRespondParent = "form-respond-parent"
  val FriendsForm = "form-friends"
  val InviteFormOpen = "invite-form-open"
  val InviteFormClass = "invite-form"
  val InviteFormBoatClass = "invite-form-boat"
  val InviteFormInputClass = "invite-form-input"
  val InviteAccept = "invite-form-accept"
  val InviteReject = "invite-form-reject"
  val DeleteForm = "form-delete"
  val FormCancel = "form-cancel"
  val CancelEditTrackId = "track-cancel"
  val EditTitleFormId = "form-edit-title"
  val EditTitleId = "edit-title"
  val TitleInputId = "title-value"
  val TrackDataId = "track-data"
  val TrackRow = "track-row"
  val TrackTitleId = "track-title"

trait BodyClasses:
  val AboutClass = "about"
  val BoatsClass = "boats"
  val ChartsClass = "charts"
  val FormsClass = "forms"
  val MapClass = "map"
  val StatsClass = "stats"
