package com.malliina.web

import com.malliina.boat.APNSConf
import com.malliina.push.apns.{KeyId, TeamId}
import com.malliina.util.AppLogger
import com.malliina.web.AppleTokenValidator.appleIssuer
import com.malliina.web.SignInWithApple.{Conf, log}
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
    keyId: KeyId,
    teamId: TeamId,
    clientId: ClientId
  )

  def secret(conf: Conf, now: Instant): Option[ClientSecret] =
    Option.when(conf.enabled):
      SignInWithApple(conf).signInWithAppleToken(now)

  def secretOrDummy(conf: Conf, now: Instant) =
    secret(conf, now).getOrElse:
      log.info(s"Sign in with Apple using ID ${conf.clientId} is disabled.")
      ClientSecret("disabled")

  object Conf:
    def siwa(enabled: Boolean, privateKey: Path): Conf = Conf(
      enabled,
      privateKey,
      KeyId("2HRJXFM6UG"),
      APNSConf.teamId,
      ClientId("com.malliina.boat.client")
    )

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
  private val header = JWSHeader.Builder(JWSAlgorithm.ES256).keyID(conf.keyId.id).build()

  def signInWithAppleToken(now: Instant): ClientSecret =
    val issuedAt = Date.from(now)
    val exp = Date.from(now.plus(179, ChronoUnit.DAYS))
    val claims = JWTClaimsSet
      .Builder()
      .issuer(conf.teamId.team)
      .issueTime(issuedAt)
      .expirationTime(exp)
      .audience(appleIssuer.value)
      .subject(conf.clientId.value)
      .build()
    val signable = SignedJWT(header, claims)
    signable.sign(signer)
    val secret = ClientSecret(signable.serialize())
    log.info(
      s"Created SIWA secret with key '${conf.keyId}' for client '${conf.clientId}' and team '${conf.teamId}'."
    )
    secret
