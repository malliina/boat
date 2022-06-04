package com.malliina.web

import com.malliina.config.{ConfigOps, ConfigReadable}
import com.malliina.http.FullUrl
import com.malliina.util.AppLogger
import com.malliina.web.AppleTokenValidator.appleIssuer
import com.malliina.web.SignInWithApple.{Conf, log}
import com.malliina.web.{ClientId, ClientSecret}
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}

import java.nio.file.{Files, Path}
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.{Base64, Date}
import scala.jdk.CollectionConverters.ListHasAsScala

object SignInWithApple:
  private val log = AppLogger(getClass)

  case class Conf(
    enabled: Boolean,
    privateKey: Path,
    keyId: String,
    teamId: String,
    clientId: ClientId
  )

  def secret(conf: Conf, now: Instant): Option[ClientSecret] =
    Option.when(conf.enabled) {
      SignInWithApple(conf).signInWithAppleToken(now)
    }

  def secretOrDummy(conf: Conf, now: Instant) =
    secret(conf, now).getOrElse {
      log.info(s"Sign in with Apple using ID ${conf.clientId} is disabled.")
      ClientSecret("disabled")
    }

  object Conf:
    implicit val reader: ConfigReadable[Conf] = ConfigReadable.config.emap { obj =>
      for
        enabled <- obj.read[Boolean]("enabled")
        keyPath <- obj.read[Path]("privateKey")
        keyId <- obj.read[String]("keyId")
        team <- obj.read[String]("teamId")
        clientId <- obj.read[ClientId]("clientId")
      yield Conf(enabled, keyPath, keyId, team, clientId)
    }

/** https://developer.apple.com/documentation/sign_in_with_apple/generate_and_validate_tokens
  */
class SignInWithApple(conf: Conf):
  log.info(
    s"Configuring Sign in with Apple using client ID '${conf.clientId}' team '${conf.teamId}' key ID '${conf.keyId}' at '${conf.privateKey}'..."
  )
  private val content = Files.readAllLines(conf.privateKey).asScala.drop(1).dropRight(1).mkString
  private val decoded = Base64.getDecoder.decode(content)
  private val keySpec = PKCS8EncodedKeySpec(decoded)
  private val keyFactory = KeyFactory.getInstance("EC")
  private val key = keyFactory.generatePrivate(keySpec).asInstanceOf[ECPrivateKey]
  private val signer = ECDSASigner(key)
  private val header = JWSHeader.Builder(JWSAlgorithm.ES256).keyID(conf.keyId).build()

  def signInWithAppleToken(now: Instant): ClientSecret =
    val issuedAt = Date.from(now)
    val exp = Date.from(now.plus(179, ChronoUnit.DAYS))
    val claims = JWTClaimsSet
      .Builder()
      .issuer(conf.teamId)
      .issueTime(issuedAt)
      .expirationTime(exp)
      .audience(appleIssuer.value)
      .subject(conf.clientId.value)
      .build()
    val signable = SignedJWT(header, claims)
    signable.sign(signer)
    ClientSecret(signable.serialize())
