package com.malliina.web

import com.malliina.http.SingleError
import org.http4s.Headers

class AuthException(val error: AuthError) extends Exception(error.message.value):
  val message = error.message
  def singleError = SingleError(error.message, error.key)

class WebAuthException(error: AuthError, val headers: Headers) extends AuthException(error)
