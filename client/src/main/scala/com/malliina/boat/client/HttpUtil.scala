package com.malliina.boat.client

import java.util.Base64

object HttpUtil {
  val Authorization = "Authorization"

  def basicAuth(username: String, password: String): KeyValue =
    KeyValue(HttpUtil.Authorization, authorizationValue(username, password))

  def authorizationValue(username: String, password: String) =
    "Basic " + Base64.getEncoder.encodeToString(s"$username:$password".getBytes("UTF-8"))
}
