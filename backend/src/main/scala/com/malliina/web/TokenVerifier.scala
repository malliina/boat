package com.malliina.web

import com.malliina.values.TokenValue

import java.time.Instant

abstract class TokenVerifier[F[_]](issuers: Seq[Issuer]) extends TokenValidator(issuers):
  def validateToken(token: TokenValue, now: Instant): F[Either[AuthError, Verified]]
