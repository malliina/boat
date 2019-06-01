package com.malliina.boat

import com.malliina.play.auth.AuthConf
import play.api.Configuration

case class AppConf(iosClientId: String,
                   webClientId: String,
                   webClientSecret: String,
                   mapboxToken: AccessToken) {
  def web = AuthConf(webClientId, webClientSecret)
}

object AppConf {
  val Name = "Boat-Tracker"

  def apply(conf: Configuration): AppConf = AppConf(
    conf.get[String]("boat.google.ios.id"),
    conf.get[String]("boat.google.web.id"),
    conf.get[String]("boat.google.web.secret"),
    AccessToken(conf.get[String]("boat.mapbox.token"))
  )
}
