package com.malliina.boat

object Constants extends Constants

trait Constants extends CookieNames with BoatHeaders {
  val BoatTokenQuery = "token"
  val DefaultSample = 4
}

object BoatHeaders extends BoatHeaders

trait BoatHeaders {
  val BoatNameHeader = "X-Boat"
  val BoatTokenHeader = "X-Token"
  val TrackNameHeader = "X-Track"
}

object CookieNames extends CookieNames

trait CookieNames {
  val LanguageName = "lang"
  val TokenCookieName = "mapboxToken"
}

object FrontKeys extends FrontKeys

trait FrontKeys extends BodyClasses with ListKeys with NavKeys with AboutKeys {
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

  val SampleKey = "sample"

  val Invisible = "invisible"
  val Visible = "visible"
}

trait AboutKeys {
  val LanguageRadios = "language-radios"
}

trait NavKeys {
  val DropdownContentId = "dropdown-content"
  val DropdownLinkId = "dropdown-link"

  val DistanceId = "distance"
  val DurationId = "duration"
  val TitleId = "text-title"
  val TopSpeedId = "top-speed"
  val WaterTempId = "water-temp"

  val Question = "question-link"
  val QuestionNav = "question-nav"

  val RouteLength = "route-length"
  val RouteText = "route-text"
}

trait ListKeys {
  val CancelEditTrackId = "track-cancel"
  val EditTitleFormId = "form-edit-title"
  val EditTitleId = "edit-title"
  val TitleInputId = "title-value"
  val TrackDataId = "track-data"
  val TrackRow = "track-row"
  val TrackTitleId = "track-title"
}

trait BodyClasses {
  val AboutClass = "about"
  val ChartsClass = "charts"
  val ListClass = "list"
  val MapClass = "map"
}
