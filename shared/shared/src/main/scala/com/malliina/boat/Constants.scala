package com.malliina.boat

object Constants extends Constants

trait Constants {
  val TokenCookieName = "mapboxToken"

  val BoatNameHeader = "X-Boat"
  val BoatTokenHeader = "X-Token"
  val TrackNameHeader = "X-Track"
  val BoatTokenQuery = "token"

  val DefaultSample = 4
}

object FrontKeys extends FrontKeys

trait FrontKeys {
  val ChartsClass = "charts"
  val ChartsId = "charts"
  val Close = "close"
  val DistanceId = "distance"
  val DropdownContentId = "dropdown-content"
  val DropdownLinkId = "dropdown-link"
  val DurationId = "duration"
  val FullLinkId = "full-list-link"
  val GraphLinkId = "graph-link"
  val Hidden = "hidden"
  val MapClass = "map"
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
