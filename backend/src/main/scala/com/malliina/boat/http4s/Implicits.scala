package com.malliina.boat.http4s

import cats.effect.Concurrent
import com.malliina.boat.{DeviceId, TrackCanonical, TrackId, TrackName}
import com.malliina.values.Username
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, Printer}
import org.http4s.circe.CirceInstances
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.http4s.{Charset, DecodeResult, EntityDecoder, EntityEncoder, MediaType, syntax}
import scalatags.generic.Frag

trait Extractors:
  object UsernameVar extends NonEmpty(Username.apply)
  object TrackNameVar extends NonEmpty(TrackName.apply)
  object TrackCanonicalVar extends NonEmpty(TrackCanonical.apply)
  object DeviceIdVar extends Id(DeviceId.apply)
  object TrackIdVar extends Id(TrackId.apply)

  abstract class NonEmpty[T](build: String => T):
    def unapply(str: String): Option[T] =
      if str.trim.nonEmpty then Option(build(str.trim)) else None

  abstract class Id[T](build: Long => T):
    def unapply(str: String): Option[T] =
      str.toLongOption.map(build)

  object DoubleVar:
    def unapply(str: String): Option[Double] = str.toDoubleOption

trait MyScalatagsInstances:
  given scalatagsEncoder[F[_], C <: Frag[?, String]](using
    charset: Charset = Charset.`UTF-8`
  ): EntityEncoder[F, C] =
    contentEncoder(MediaType.text.html)

  private def contentEncoder[F[_], C <: Frag[?, String]](
    mediaType: MediaType
  )(using charset: Charset): EntityEncoder[F, C] =
    EntityEncoder
      .stringEncoder[F]
      .contramap[C](content => content.render)
      .withContentType(`Content-Type`(mediaType, charset))

object JsonInstances extends JsonInstances

trait JsonInstances extends CirceInstances:
  override protected val defaultPrinter: Printer = Printer.noSpaces.copy(dropNullValues = true)

  given circeJsonEncoder[F[_], T: Encoder]: EntityEncoder[F, T] =
    jsonEncoder[F].contramap[T](t => t.asJson)

  def jsonBody[F[_]: Concurrent, A](using decoder: Decoder[A]): EntityDecoder[F, A] =
    jsonDecoder[F].flatMapR: json =>
      json
        .as[A]
        .fold(
          errors => DecodeResult.failureT[F, A](JsonException(errors, json)),
          ok => DecodeResult.successT(ok)
        )

abstract class Implicits[F[_]]
  extends syntax.AllSyntax
  with Http4sDsl[F]
  with MyScalatagsInstances
  with JsonInstances
  with Extractors
