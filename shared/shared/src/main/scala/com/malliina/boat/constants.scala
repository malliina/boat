package com.malliina.boat

object Constants extends Constants

trait Constants {

  val BoatNameHeader = "X-Boat"
  val BoatTokenHeader = "X-Token"
  val BoatTokenQuery = "token"

  val DefaultSample = 4

  val TokenCookieName = "mapboxToken"
  val TrackNameHeader = "X-Track"
}

object FrontKeys extends FrontKeys

trait FrontKeys extends BodyClasses with ListKeys {
  val ChartsId = "charts"
  val Close = "close"
  val DistanceId = "distance"
  val DropdownContentId = "dropdown-content"
  val DropdownLinkId = "dropdown-link"
  val DurationId = "duration"

  val FullLinkId = "full-list-link"
  val GraphLinkId = "graph-link"
  val Hidden = "hidden"
  val MapId = "map"
  val Modal = "modal"
  val ModalId = "modal"
  val PersonLink = "person-link"
  val Question = "question-link"
  val QuestionNav = "question-nav"
  val SampleKey = "sample"
  val TopSpeedId = "top-speed"
  val Visible = "visible"
  val WaterTempId = "water-temp"
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
  val ChartsClass = "charts"
  val MapClass = "map"
  val ListClass = "list"
}
