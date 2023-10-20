package com.malliina.web

import com.malliina.values.{ErrorMessage, IdToken, RefreshToken}
import com.malliina.web.OAuthKeys.{CodeKey, State}
import io.circe.Codec
import org.http4s.UrlForm

case class AppleResponse(code: Code, state: String)

object AppleResponse:
  def apply(form: UrlForm): Either[ErrorMessage, AppleResponse] =
    def read(key: String) = form.getFirst(key).toRight(ErrorMessage(s"Not found: '$key' in $form."))
    for
      code <- read(CodeKey).map(Code.apply)
      state <- read(State)
    yield AppleResponse(code, state)

case class RefreshTokenResponse(id_token: IdToken, refresh_token: RefreshToken)
  derives Codec.AsObject:
  def refreshToken = refresh_token
  def idToken = id_token

case class RevokeResult(success: Boolean, statusCode: Int, token: RefreshToken, clientId: ClientId)
  derives Codec.AsObject
