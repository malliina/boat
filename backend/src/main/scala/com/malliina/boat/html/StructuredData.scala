package com.malliina.boat.html

import com.malliina.boat.AppConf
import io.circe.Json
import io.circe.syntax.EncoderOps
import scalatags.Text.all.{Frag, `type`, attr, content, meta, modifier, raw, script, stringAttr}

import scala.language.implicitConversions

object StructuredData:
  val property = attr("property")
  val typeof = attr("typeof")
  val vocab = attr("vocab")

  implicit def jsonFrag(json: Json): Frag = raw(json.noSpaces)
  implicit def stringJson(s: String): Json = s.asJson

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
