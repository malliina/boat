package com.malliina.polestar

import cats.Show
import com.malliina.http.FullUrl
import com.malliina.values.Literals.err
import com.malliina.values.{ErrorMessage, JsonCompanion}
import io.circe.Codec

opaque type VIN = String

object VIN extends JsonCompanion[String, VIN]:
  given Show[VIN] = Show(t => write(t))

  override def apply(str: String): VIN = str

  override def build(input: String): Either[ErrorMessage, VIN] =
    if input.isBlank then Left(err"VIN must not be blank.")
    else Right(input.trim)

  override def write(t: VIN): String = t

case class OpenIdConfiguration(authorization_endpoint: FullUrl) derives Codec.AsObject:
  def authorizationEndpoint = authorization_endpoint
