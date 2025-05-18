package com.malliina.polestar

import cats.effect.Async
import cats.syntax.all.{toFlatMapOps, toFunctorOps}
import com.malliina.http.UrlSyntax.https
import com.malliina.http.{FullUrl, HttpClient}
import com.malliina.polestar.Polestar.{Creds, Tokens}
import com.malliina.polestar.PolestarAuth.log
import com.malliina.util.AppLogger
import com.malliina.values.{ErrorMessage, RefreshToken, Username}

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import scala.util.Random

object PolestarAuth:
  private val log = AppLogger(getClass)

class PolestarAuth[F[_]: Async](val http: HttpClient[F]):
  private val F = Async[F]
  private val baseUrl = https"polestarid.eu.polestar.com"
  private val configUrl = baseUrl / ".well-known" / "openid-configuration"
  private val scope = "openid profile email customer:attributes"
  private val redirect = https"www.polestar.com/sign-in-callback"
  private val clientId = "l3oopkc_10"

  def fetchTokens(creds: Creds): F[Tokens] =
    val username = creds.username
    val state = generateState
    val verifier = generateVerifier
    for
      conf <- openIdConfiguration
      res <- http.get(conf.authorizationEndpoint.query(params(state, verifier)))
      action <- fromEither(findAction(res.asString, creds.username))
      cookies = res.headers.getOrElse("set-cookie", Nil).map(_.takeWhile(c => c != ';'))
      url = baseUrl.withUri(action)
      res <- http.postForm(
        url,
        Map("pf.username" -> username.name, "pf.pass" -> creds.password.pass),
        Map("cookie" -> cookies.mkString(";"))
      )
      redir = res.headers
        .getOrElse("location", Nil)
        .headOption
        .toRight(ErrorMessage(s"No location header in auth of '$username'."))
        .flatMap(loc => FullUrl.build(loc))
      redirUrl <- fromEither(redir)
      code <- fromEither(
        query(redirUrl).toMap
          .get("code")
          .toRight(ErrorMessage(s"No code in auth of '$username'.."))
      )
      tokens <- http.postFormAs[Tokens](conf.tokenEndpoint, tokenRequest(code, verifier))
    yield
      log.info(s"Obtained Polestar tokens of '$username'.")
      tokens

  def refresh(token: RefreshToken): F[Tokens] =
    for
      conf <- openIdConfiguration
      tokens <- http.postFormAs[Tokens](conf.tokenEndpoint, refreshRequest(token))
    yield tokens

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

  private def generateState: String = generateString

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

  private def findAction(text: String, username: Username) = text match
    case s"""${init}window.globalContext${json}action: "${path}",${rest}""" => Right(path)
    case _ => Left(ErrorMessage(s"Action not found from '$text' in auth of '$username'."))

  private def openIdConfiguration =
    http.getAs[OpenIdConfiguration](configUrl)

  private def fromEither[T](e: Either[ErrorMessage, T]): F[T] =
    F.fromEither(e.left.map(e => PolestarAuthException(e.message)))

  private def query(url: FullUrl) =
    url.url
      .dropWhile(c => c != '?')
      .drop(1)
      .split("&")
      .toList
      .flatMap: pair =>
        pair.split("=").toList match
          case h :: t :: Nil => Seq(h -> t)
          case _             => Nil

  private def tokenRequest(code: String, verifier: String) = Map(
    "grant_type" -> "authorization_code",
    "client_id" -> clientId,
    "code" -> code,
    "redirect_uri" -> redirect.url,
    "code_verifier" -> verifier
  )

  private def refreshRequest(token: RefreshToken) = Map(
    "grant_type" -> "refresh_token",
    "client_id" -> clientId,
    "refresh_token" -> token.token
  )
