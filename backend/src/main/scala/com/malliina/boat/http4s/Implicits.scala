package com.malliina.boat.http4s

import cats.Applicative
import cats.effect.Concurrent
import com.malliina.boat.{DeviceId, S3Key, TrackCanonical, TrackId, TrackName}
import com.malliina.http4s.BasicService
import com.malliina.values.{ErrorMessage, Username}
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, Printer}
import org.http4s.circe.CirceInstances
import org.http4s.headers.`Content-Type`
import org.http4s.{Charset, DecodeResult, EntityDecoder, EntityEncoder, MediaType}
import scalatags.generic.Frag

trait Extractors:
  object UsernameVar extends Validating(Username.build)
  object TrackNameVar extends Validating(TrackName.build)
  object TrackCanonicalVar extends Validating(TrackCanonical.build)
  object DeviceIdVar extends Id(DeviceId.build)
  object TrackIdVar extends Id(TrackId.build)
  object S3KeyVar extends Validating(S3Key.build)

  abstract class Validating[T](build: String => Either[ErrorMessage, T]):
    def unapply(str: String): Option[T] =
      build(str).toOption

  abstract class Id[T](build: Long => Either[ErrorMessage, T]):
    def unapply(str: String): Option[T] =
      str.toLongOption.flatMap(build(_).toOption)

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

abstract class Implicits[F[_]: Applicative]
  extends BasicService[F]
  with MyScalatagsInstances
  with JsonInstances
  with Extractors
