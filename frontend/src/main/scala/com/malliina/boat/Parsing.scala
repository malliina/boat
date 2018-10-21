package com.malliina.boat

import play.api.libs.json._

import scala.scalajs.js
import scala.scalajs.js.JSON

object Parsing extends Parsing

trait Parsing {
  def toJson[T: Writes](t: T): js.Dynamic =
    JSON.parse(Json.stringify(Json.toJson(t)))

  def asJson[T: Reads](in: js.Any): Either[JsonError, T] =
    validate[T](Json.parse(stringifyAny(in)))

  def stringifyAny(any: js.Any) = JSON.stringify(any)

  def stringify[T: Writes](t: T) = Json.stringify(Json.toJson(t))

  def validate[T: Reads](json: JsValue): Either[JsonError, T] =
    json.validate[T].asEither.left.map(err => JsonError(JsError(err), json))
}

case class JsonError(error: JsError, json: JsValue) {
  def describe = s"JSON error $error for JSON '$json'."
}
