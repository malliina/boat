package com.malliina.polestar

import cats.effect.Async
import cats.effect.kernel.Resource
import cats.syntax.all.toFunctorOps
import com.malliina.boat.{CarsTelematics, VIN}
import com.malliina.http.UrlSyntax.https
import com.malliina.http.io.HttpClientIO
import com.malliina.http.{FullUrl, HttpClient}
import com.malliina.values.{AccessToken, Password, RefreshToken, Username}
import io.circe.syntax.EncoderOps
import io.circe.{Codec, Decoder, Encoder, Json}

object Polestar:
  case class Creds(username: Username, password: Password) derives Decoder
  object Creds:
    given Encoder[Creds] = c =>
      Json.obj("pf.username" -> c.username.asJson, "pf.pass" -> c.password.asJson)

  trait PolestarTokens:
    def accessToken: AccessToken
    def refreshToken: RefreshToken

  case class Tokens(access_token: AccessToken, refresh_token: RefreshToken, expires_in: Long)
    extends PolestarTokens derives Codec.AsObject:
    def accessToken = access_token
    def refreshToken = refresh_token
    def expiresInSeconds = expires_in

  def resource[F[_]: Async]: Resource[F, Polestar[F]] =
    HttpClientIO
      .configure(_.followRedirects(false))
      .map: http =>
        Polestar(http)

class Polestar[F[_]: Async](http: HttpClient[F]):
  val auth = PolestarAuth(http)
  private val myStarUrl = https"pc-api.polestar.com/eu-north-1/mystar-v2/"
  private val carsQuery = resource("get-cars.graphql")
  private val telematicsQuery = resource("get-telematics.graphql")

  def fetchCars(token: AccessToken): F[Seq[PolestarCarInfo]] =
    graphQuery[CarsResponse](GraphQuery(carsQuery, None), token)
      .map(_.data.getConsumerCarsV2)

  def fetchTelematics(vin: VIN, token: AccessToken): F[CarsTelematics] =
    val query = GraphQuery(telematicsQuery, Option(VINSVariable(Seq(vin))))
    graphQuery[TelematicsResponse](query, token)
      .map(_.data.carTelematicsV2)

  private def graphQuery[T: Decoder](q: GraphQuery, token: AccessToken): F[T] =
    http.postAs[GraphQuery, T](
      myStarUrl,
      q,
      Map("Authorization" -> s"Bearer $token", "Accept" -> "application/graphql-response+json")
    )

  private def resource(file: String) =
    scala.io.Source
      .fromResource(s"com/malliina/polestar/$file")
      .mkString
