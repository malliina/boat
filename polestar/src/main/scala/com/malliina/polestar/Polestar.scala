package com.malliina.polestar

import cats.effect.Async
import cats.effect.kernel.Resource
import cats.syntax.all.{toFlatMapOps, toFunctorOps}
import com.malliina.http.{HttpClient, OkClient}
import com.malliina.http.UrlSyntax.https
import com.malliina.http.io.HttpClientF2
import com.malliina.polestar.Polestar.Creds
import com.malliina.values.{ErrorMessage, Password, Username}
import io.circe.syntax.EncoderOps
import io.circe.{Encoder, Json}
import okhttp3.OkHttpClient

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import scala.util.Random

object Polestar:
  case class Creds(username: Username, password: Password)
  object Creds:
    given Encoder[Creds] = c =>
      Json.obj("pf.username" -> c.username.asJson, "pf.pass" -> c.password.asJson)

  private def httpResource[F[_]: Async]: Resource[F, HttpClientF2[F]] =
    Resource.make(
      Async[F].delay(new HttpClientF2(OkHttpClient.Builder().followRedirects(false).build()))
    )(c => Async[F].delay(c.close()))

  def resource[F[_]: Async] = httpResource.map(http => Polestar(http))

class Polestar[F[_]: Async](http: HttpClient[F]):
  val baseUrl = https"polestarid.eu.polestar.com"
  val configUrl = baseUrl / ".well-known" / "openid-configuration"

  val redirect = https"www.polestar.com/sign-in-callback"
  val scope = "openid profile email customer:attributes"
  val cookies = Seq("PF", "PF.PERSISTENT")
  val clientId = "l3oopkc_10"
  val myStarUrl = https"pc-api.polestar.com/eu-north-1/mystar-v2/"

  def login(username: Username, password: Password, vin: VIN) =
    42

  def code(creds: Creds): F[Option[String]] =
    val verifier = generateVerifier
    val state = generateState
    for
      path <- resumePath(state, verifier)
      url = baseUrl.withUri(path)
      res <- http.postForm(
        url,
        Map("pf.username" -> creds.username.name, "pf.pass" -> creds.password.pass)
      )
      _ = println(s"Got ${res.code} as ${res.asString} with ${res.headers} from $url")
    yield None // res.headers.get("Location").flatMap(_.headOption)

  def resumePath(state: String, verifier: String) =
    for
      conf <- openIdConfiguration
      auth <- http.get(conf.authorizationEndpoint.query(params(state, verifier)))
      action <- Async[F].fromEither(findAction(auth.asString).left.map(e => Exception(e.message)))
    yield action

  def openIdConfiguration = http.getAs[OpenIdConfiguration](configUrl)

  private def params(state: String, verifier: String) =
    Map(
      "client_id" -> clientId,
      "redirect_uri" -> redirect.url,
      "response_type" -> "code",
      "scope" -> scope,
      "state" -> state,
      "code_challenge" -> computeChallenge(verifier),
      "code_challenge_method" -> "S256",
      "response_mode" -> "query"
    )

  private def randomString(length: Int) =
    Random.alphanumeric.filter(r => r.isLower && r.isLetter).take(length).mkString

  def generateVerifier: String = generateString

  def generateState: String = generateString

  private def generateString: String =
    Base64.getUrlEncoder
      .withoutPadding()
      .encodeToString(randomString(32).getBytes(StandardCharsets.UTF_8))

  private def computeChallenge(verifier: String): String =
    val bytes = verifier.getBytes("US-ASCII")
    val md = MessageDigest.getInstance("SHA-256")
    md.update(bytes, 0, bytes.length)
    val digest = md.digest()
    import org.apache.commons.codec.binary.Base64 as CBase64
    CBase64.encodeBase64URLSafeString(digest)

  private def findAction(text: String) = text match
    case s"""${init}window.globalContext${json}action: "${path}",${rest}""" => Right(path)
    case _ => Left(ErrorMessage("Action not found from '$text'."))
