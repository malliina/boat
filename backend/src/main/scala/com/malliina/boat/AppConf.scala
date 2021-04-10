package com.malliina.boat

import com.malliina.web.{AuthConf, ClientId, ClientSecret}

case class AppConf(
  iosClientId: ClientId,
  webClientId: ClientId,
  webClientSecret: ClientSecret,
  mapboxToken: AccessToken
) {
  def web = AuthConf(webClientId, webClientSecret)
}

object AppConf {
  val Name = "Boat-Tracker"

//  def apply(conf: Configuration): AppConf = AppConf(
//    conf.get[String]("boat.google.ios.id"),
//    conf.get[String]("boat.google.web.id"),
//    conf.get[String]("boat.google.web.secret"),
//    AccessToken(conf.get[String]("boat.mapbox.token"))
//  )
}
