package com.malliina.web

class AuthException(val error: AuthError) extends Exception(error.message.value):
  def message = error.message
