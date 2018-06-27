package com.malliina.boat

object Constants extends Constants

trait Constants {
  val TokenCookieName = "mapboxToken"

  val BoatNameHeader = "X-Boat"
  val BoatTokenHeader = "X-Token"
  val TrackNameHeader = "X-Track"
  val BoatTokenQuery = "token"
}

object FrontKeys {
  val Close = "close"
  val Distance = "distance"
  val DropdownContentId = "dropdown-content"
  val DropdownLinkId = "dropdown-link"
  val Hidden = "hidden"
  val MapClass = "map"
  val MapId = "map"
  val Modal = "modal"
  val ModalId = "modal"
  val PersonLink = "person-link"
  val Question = "question-link"
  val QuestionNav = "question-nav"
  val Visible = "visible"
}
