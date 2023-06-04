package com.malliina.boat.auth

import com.malliina.values.Email
import org.http4s.Headers

import java.time.Instant

trait EmailAuth[F[_]]:

  /** Fails with [[com.malliina.boat.db.IdentityException]] if authentication fails.
    *
    * @return
    *   the authenticated user's email address
    */
  def authEmail(headers: Headers, now: Instant): F[Email]
