package com.malliina.boat.http4s

import cats.effect.{IO, Sync}
import com.malliina.boat.{DeviceId, TrackCanonical, TrackId, TrackName}
import com.malliina.html.TagPage
import com.malliina.values.Username
import org.http4s.dsl.Http4sDsl
import org.http4s.play.PlayInstances
import org.http4s.scalatags.ScalatagsInstances
import org.http4s.{DecodeResult, EntityDecoder, EntityEncoder, syntax}
import play.api.libs.json.{JsError, Json, Reads, Writes}

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
  implicit def htmlEncoder[F[_]]: EntityEncoder[F, TagPage] = scalatagsEncoder[F].contramap(_.tags)
}

object JsonInstances extends JsonInstances

trait JsonInstances extends PlayInstances {
  implicit def playJsonEncoder[F[_], T: Writes]: EntityEncoder[F, T] =
    jsonEncoder[F].contramap[T](t => Json.toJson(t))

  def jsonBody[F[_]: Sync, A](implicit decoder: Reads[A]): EntityDecoder[F, A] =
    jsonDecoder[F].flatMapR { json =>
      decoder
        .reads(json)
        .fold(
          errors => DecodeResult.failure(new JsonException(JsError(errors), json)),
          ok => DecodeResult.success(ok)
        )
    }
}

abstract class Implicits[F[_]]
  extends syntax.AllSyntaxBinCompat
  with Http4sDsl[F]
  with HtmlInstances
  with JsonInstances
  with Extractors

object Implicits extends Implicits[IO]
