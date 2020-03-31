package com.malliina.boat

import play.api.libs.json.JsLookupResult

object JsonUtils {
  implicit class JsLookupResultOps(val result: JsLookupResult) extends AnyVal {
    def nonEmptyOpt = MaritimeJson.nonEmptyOpt(result)
  }
}
