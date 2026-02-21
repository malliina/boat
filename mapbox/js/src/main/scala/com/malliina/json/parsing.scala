package com.malliina.json

import io.circe.parser.decode
import io.circe.scalajs.{convertJsonToJs, decodeJs}
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, DecodingFailure, Encoder, Json, Printer}

import scala.scalajs.js
import scala.scalajs.js.JSON

object Parsing extends Parsing

trait Parsing:
  private val printer = Printer.noSpaces.copy(dropNullValues = true)

  extension [T: Encoder](t: T) def asJs: js.Any = convertJsonToJs(t.asJson.deepDropNullValues)

  extension (any: js.Any)
    def as[T: Decoder]: Either[JsonError, T] =
      Parsing.asJson[T](any)

    def decoded[T: Decoder]: Either[JsonError, T] = decodeJs[T](any).left.map:
      case ce: io.circe.Error =>
        JsonError(ce)
      case err =>
        JsonError(io.circe.ParsingFailure(s"Failed to parse JavaScript object.", err))

  def toJson[T: Encoder](t: T): js.Dynamic =
    JSON.parse(t.asJson.printWith(printer))

  def asJson[T: Decoder](in: js.Any): Either[JsonError, T] =
    decode[T](stringifyAny(in)).left.map(err => JsonError(err))

  private def stringifyAny(any: js.Any) = JSON.stringify(any)

  def stringify[T: Encoder](t: T): String = t.asJson.printWith(printer)

  def validate[T: Decoder](json: Json): Either[JsonError, T] =
    json.as[T].left.map(err => JsonError(DecodingFailure(err.message, Nil), json))

case class JsonError(error: io.circe.Error, json: Option[Json]):
  def describe = json.fold(s"JSON error $error")(body => s"JSON error $error for JSON '$body'.")

object JsonError:
  def apply(e: io.circe.Error): JsonError = JsonError(e, None)
  def apply(e: io.circe.Error, body: Json): JsonError = JsonError(e, Option(body))
