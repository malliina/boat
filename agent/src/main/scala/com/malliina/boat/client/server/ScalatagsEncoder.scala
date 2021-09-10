package com.malliina.boat.client.server

import org.http4s.headers.`Content-Type`
import org.http4s.{Charset, DefaultCharset, EntityEncoder, MediaType}
import scalatags.generic.Frag

trait ScalatagsEncoder:
  implicit def scalatagsEncoder[F[_], C <: Frag[?, String]](implicit
    charset: Charset = DefaultCharset
  ): EntityEncoder[F, C] =
    contentEncoder(MediaType.text.html)

  private def contentEncoder[F[_], C <: Frag[?, String]](
    mediaType: MediaType
  )(implicit charset: Charset): EntityEncoder[F, C] =
    EntityEncoder
      .stringEncoder[F]
      .contramap[C](content => content.render)
      .withContentType(`Content-Type`(mediaType, charset))
