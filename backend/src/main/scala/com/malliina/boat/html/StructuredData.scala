package com.malliina.boat.html

import com.malliina.boat.AppConf
import play.api.libs.json.{JsValue, Json}
import scalatags.Text.all._

object StructuredData {
  val property = attr("property")
  val typeof = attr("typeof")
  val vocab = attr("vocab")

  implicit def jsonFrag(json: JsValue): Frag = raw(Json.stringify(json))

  val appStructuredData = script(`type` := "application/ld+json")(
    Json.obj(
      "@context" -> "http://schema.org",
      "@type" -> "SoftwareApplication",
      "name" -> AppConf.Name,
      "url" -> "https://www.boat-tracker.com",
      "applicationCategory" -> "https://schema.org/TravelApplication",
      "operatingSystem" -> "iOS"
    )
  )

  val appLinkMetadata = modifier(
    meta(property := "al:ios:app_store_id", content := "1434203398"),
    meta(property := "al:ios:app_name", content := AppConf.Name)
  )
}
