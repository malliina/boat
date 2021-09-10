package com.malliina.boat.auth

import cats.effect.IO
import com.malliina.values.Email
import org.http4s.Headers

trait EmailAuth:

  /** Fails with [[com.malliina.boat.db.IdentityException]] if authentication fails.
    *
    * @return
    *   the authenticated user's email address
    */
  def authEmail(headers: Headers): IO[Email]
