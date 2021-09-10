package com.malliina.boat.client

import java.nio.charset.StandardCharsets
import java.util.Base64

import com.malliina.values.Username

object HttpUtil:
  val Authorization = "Authorization"

  def basicAuth(username: Username, password: String): KeyValue =
    KeyValue(HttpUtil.Authorization, authorizationValue(username, password))

  def authorizationValue(username: Username, password: String) =
    "Basic " + Base64.getEncoder.encodeToString(
      s"$username:$password".getBytes(StandardCharsets.UTF_8)
    )
