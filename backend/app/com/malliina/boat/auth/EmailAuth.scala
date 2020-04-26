package com.malliina.boat.auth

import com.malliina.values.Email
import play.api.mvc.RequestHeader

import scala.concurrent.Future

trait EmailAuth {

  /** Fails with [[com.malliina.boat.db.IdentityException]] if authentication fails.
    *
    * @return the authenticated user's email address
    */
  def authEmail(rh: RequestHeader): Future[Email]
}
