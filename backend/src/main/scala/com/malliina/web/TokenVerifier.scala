package com.malliina.web

import cats.effect.IO
import com.malliina.values.TokenValue

import java.time.Instant

abstract class TokenVerifier(issuers: Seq[Issuer]) extends TokenValidator(issuers):
  def validateToken(token: TokenValue, now: Instant): IO[Either[AuthError, Verified]]
