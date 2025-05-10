package com.malliina.polestar

import cats.effect.kernel.Resource
import cats.effect.{Async, Ref}
import cats.syntax.all.{catsSyntaxApplicativeError, catsSyntaxFlatMapOps, toFlatMapOps, toFunctorOps}
import com.malliina.http.UrlSyntax.https
import com.malliina.http.io.HttpClientF2
import com.malliina.http.{FullUrl, ResponseException}
import com.malliina.polestar.Polestar.Tokens
import com.malliina.util.AppLogger
import com.malliina.values.{AccessToken, Password, RefreshToken, Username}
import fs2.io.file.Files
import io.circe.syntax.EncoderOps
import io.circe.{Codec, Decoder, Encoder, Json}
import okhttp3.OkHttpClient

object Polestar:
  private val log = AppLogger(getClass)

  case class Creds(username: Username, password: Password)
  object Creds:
    given Encoder[Creds] = c =>
      Json.obj("pf.username" -> c.username.asJson, "pf.pass" -> c.password.asJson)

  case class Tokens(access_token: AccessToken, refresh_token: RefreshToken, expires_in: Long)
    derives Codec.AsObject:
    def accessToken = access_token
    def refreshToken = refresh_token
    def expiresInSeconds = expires_in

  def httpResource[F[_]: Async]: Resource[F, HttpClientF2[F]] =
    Resource.make(
      Async[F].delay(new HttpClientF2(OkHttpClient.Builder().followRedirects(false).build()))
    )(c => Async[F].delay(c.close()))

  def resource[F[_]: {Async, Files}](creds: Creds) =
    httpResource.evalMap: http =>
      val tc = TokenClient[F](http)
      for
        ts <- tc.refreshOrFetchTokens(creds)
        ref <- Ref[F].of[Tokens](ts)
      yield Polestar(tc, ref)

class Polestar[F[_]: Async](tokenClient: TokenClient[F], tokensRef: Ref[F, Tokens]):
  private val F = Async[F]
  private val myStarUrl = https"pc-api.polestar.com/eu-north-1/mystar-v2/"
  private val http = tokenClient.http
  private val carsQuery = resource("get-cars.graphql")
  private val telematicsQuery = resource("get-telematics.graphql")

  def fetchCars(): F[Seq[CarInfo]] =
    graphQuery[CarsResponse](GraphQuery(carsQuery, None))
      .map(_.data.getConsumerCarsV2)

  def fetchTelematics(vin: VIN): F[CarTelematics] = withToken: token =>
    val query = GraphQuery(telematicsQuery, Option(VINVariable(vin)))
    graphQuery[TelematicsResponse](query)
      .map(_.data.carTelematics)

  private def graphQuery[T: Decoder](q: GraphQuery): F[T] = withToken: token =>
    http.postAs[GraphQuery, T](
      myStarUrl,
      q,
      Map("Authorization" -> s"Bearer $token", "Accept" -> "application/graphql-response+json")
    )

  private def withToken[T](task: AccessToken => F[T]): F[T] =
    tokensRef.get.flatMap: tokens =>
      task(tokens.accessToken).handleErrorWith:
        case re: ResponseException if re.response.code == 401 =>
          tokenClient
            .refreshCached()
            .flatMap: tokens =>
              tokensRef.set(tokens) >> task(tokens.accessToken)
        case t => F.raiseError(t)

  private def resource(file: String) =
    scala.io.Source
      .fromResource(s"com/malliina/polestar/$file")
      .mkString
