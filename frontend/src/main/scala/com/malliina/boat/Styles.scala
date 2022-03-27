package com.malliina.boat

import io.circe.*
import io.circe.syntax.EncoderOps

import scala.language.implicitConversions

object Styles:
  implicit def string(s: String): Json = s.asJson
  implicit def int(i: Int): Json = i.asJson

  val colorBySpeed = Json.arr(
    "interpolate",
    Json.arr("linear"),
    Json.arr("get", TimedCoord.SpeedKey),
    5,
    "rgb(0,255,150)",
    10,
    "rgb(50,150,50)",
    15,
    "rgb(100,255,50)",
    20,
    "rgb(255,255,0)",
    25,
    "rgb(255,213,0)",
    28,
    "rgb(255,191,0)",
    30,
    "rgb(255,170,0)",
    32,
    "rgb(255,150,0)",
    33,
    "rgb(255,140,00)",
    35,
    "rgb(255,128,0)",
    37,
    "rgb(255,85,0)",
    38,
    "rgb(255,42,0)",
    39,
    "rgb(255,21,0)",
    40,
    "rgb(255,0,0)"
  )
