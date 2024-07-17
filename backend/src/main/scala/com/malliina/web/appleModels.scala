package com.malliina.web

import com.malliina.http4s.FormReadableT
import com.malliina.values.{IdToken, RefreshToken}
import com.malliina.web.OAuthKeys.{CodeKey, State}
import io.circe.Codec

case class AppleResponse(code: Code, state: String)

object AppleResponse:
  given FormReadableT[AppleResponse] = FormReadableT.reader.emap: form =>
    for
      code <- form.read[Code](CodeKey)
      state <- form.read[String](State)
    yield AppleResponse(code, state)

case class RefreshTokenResponse(id_token: IdToken, refresh_token: RefreshToken)
  derives Codec.AsObject:
  def refreshToken = refresh_token
  def idToken = id_token

case class RevokeResult(success: Boolean, statusCode: Int, token: RefreshToken, clientId: ClientId)
  derives Codec.AsObject
