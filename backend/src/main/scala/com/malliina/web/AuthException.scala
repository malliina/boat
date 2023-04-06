package com.malliina.web

import com.malliina.boat.SingleError
import org.http4s.Headers

class AuthException(val error: AuthError, val headers: Headers)
  extends Exception(error.message.value):
  def message = error.message
  def singleError = SingleError(error.message, error.key)
