package com.malliina.boat.http4s

import cats.effect.{IO, Sync}
import com.malliina.boat.{DeviceId, TrackCanonical, TrackId, TrackName}
import com.malliina.html.TagPage
import com.malliina.values.Username
import io.circe.{Decoder, Encoder, Printer}
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceInstances
import org.http4s.dsl.Http4sDsl
import org.http4s.scalatags.ScalatagsInstances
import org.http4s.{DecodeResult, EntityDecoder, EntityEncoder, syntax}
import scalatags.Text

trait Extractors {
  object UsernameVar extends NonEmpty(Username.apply)
  object TrackNameVar extends NonEmpty(TrackName.apply)
  object TrackCanonicalVar extends NonEmpty(TrackCanonical.apply)
  object DeviceIdVar extends Id(DeviceId.apply)
  object TrackIdVar extends Id(TrackId.apply)

  abstract class NonEmpty[T](build: String => T) {
    def unapply(str: String): Option[T] =
      if (str.trim.nonEmpty) Option(build(str.trim)) else None
  }

  abstract class Id[T](build: Long => T) {
    def unapply(str: String): Option[T] =
      str.toLongOption.map(build)
  }

  object DoubleVar {
    def unapply(str: String): Option[Double] = str.toDoubleOption
  }
}

trait HtmlInstances extends ScalatagsInstances {
  implicit def htmlEncoder[F[_]]: EntityEncoder[F, TagPage] =
    scalatagsEncoder[F, Text.TypedTag[String]].contramap(_.tags)
}

object JsonInstances extends JsonInstances

trait JsonInstances extends CirceInstances {
  override protected val defaultPrinter: Printer = Printer.noSpaces.copy(dropNullValues = true)

  implicit def playJsonEncoder[F[_], T: Encoder]: EntityEncoder[F, T] =
    jsonEncoder[F].contramap[T](t => t.asJson)

  def jsonBody[F[_]: Sync, A](implicit decoder: Decoder[A]): EntityDecoder[F, A] =
    jsonDecoder[F].flatMapR { json =>
      json
        .as[A]
        .fold(
          errors => DecodeResult.failureT(new JsonException(errors, json)),
          ok => DecodeResult.successT(ok)
        )
    }
}

abstract class Implicits[F[_]]
  extends syntax.AllSyntaxBinCompat
  with Http4sDsl[F]
  with HtmlInstances
  with JsonInstances
  with CirceInstances
  with Extractors

object Implicits extends Implicits[IO]
