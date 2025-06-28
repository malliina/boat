package com.malliina.boat

import com.malliina.values.AccessToken
import com.malliina.web.{AuthConf, ClientId, ClientSecret}

case class AppConf(
  iosClientId: ClientId,
  webClientId: ClientId,
  webClientSecret: ClientSecret,
  mapboxToken: AccessToken
):
  def web = AuthConf(webClientId, webClientSecret)

object AppConf:
  val Name = "Boat-Tracker"
  val CarName = "Car-Map"
