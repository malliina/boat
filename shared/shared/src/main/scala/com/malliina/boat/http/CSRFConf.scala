package com.malliina.boat.http

object CSRFConf extends CSRFConf

trait CSRFConf:
  val CsrfTokenName = "csrfToken"
  val CsrfCookieName = "csrfToken"
  val CsrfHeaderName = "Csrf-Token"
  val CsrfTokenNoCheck = "nocheck"
