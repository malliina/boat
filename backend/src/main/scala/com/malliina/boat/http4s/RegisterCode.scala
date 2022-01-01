package com.malliina.boat.http4s

import com.malliina.web.Code
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class RegisterCode(code: Code)

object RegisterCode:
  implicit val json: Codec[RegisterCode] = deriveCodec[RegisterCode]
