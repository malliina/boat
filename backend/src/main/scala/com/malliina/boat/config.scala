package com.malliina.boat

import com.malliina.boat.auth.SecretKey
import com.malliina.config.ConfigReadable
import com.malliina.config.ConfigReadable.ConfigOps
import com.malliina.database.Conf
import com.malliina.push.apns.{KeyId, TeamId}
import com.malliina.util.FileUtils
import com.malliina.values.ErrorMessage
import com.malliina.web.{AuthConf, ClientId, ClientSecret, SignInWithApple}
import com.typesafe.config.{Config, ConfigFactory}

sealed trait AppMode:
  def isProd = this == AppMode.Prod

object AppMode:
  case object Prod extends AppMode
  case object Dev extends AppMode
  val fromBuild: AppMode = unsafe(BuildInfo.mode)

  given ConfigReadable[AppMode] = ConfigReadable.string.emapParsed: s =>
    fromString(s)

  def unsafe(in: String): AppMode =
    fromString(in).fold(err => throw new IllegalArgumentException(err.message), identity)

  def fromString(in: String): Either[ErrorMessage, AppMode] = in match
    case "prod" => Right(Prod)
    case "dev"  => Right(Dev)
    case other  => Left(ErrorMessage(s"Invalid mode: '$other'. Must be 'prod' or 'dev'."))

object LocalConf:
  private val homeDir = FileUtils.userHome
  val appDir = homeDir.resolve(".boat")
  private val localConfFile = appDir.resolve("boat.conf")
  val isProd = BuildInfo.mode == "prod"
  val localConf = ConfigFactory.parseFile(localConfFile.toFile).withFallback(ConfigFactory.load())
  val conf = if isProd then ConfigFactory.load("application-prod.conf") else localConf
case class MapboxConf(token: AccessToken)
case class AisAppConf(enabled: Boolean)
case class AppleConf(id: ClientId)
case class MicrosoftConf(boat: WebConf, car: WebConf)
case class WebConf(id: ClientId, secret: ClientSecret):
  def webAuthConf = AuthConf(id, secret)
case class GoogleConf(ios: AppleConf, web: WebConf):
  def webAuthConf = AuthConf(web.id, web.secret)
case class APNSConf(enabled: Boolean, privateKey: String, keyId: KeyId, teamId: TeamId)
case class FCMConf(apiKey: String)
case class PushConf(apns: APNSConf, fcm: FCMConf)

case class BoatConf(
  mapbox: MapboxConf,
  ais: AisAppConf,
  secret: SecretKey,
  db: Conf,
  google: GoogleConf,
  microsoft: MicrosoftConf,
  apple: SignInWithApple.Conf,
  push: PushConf
)

object BoatConf:
  given ConfigReadable[AccessToken] = byString(s => AccessToken(s))
  given ConfigReadable[SecretKey] = byString(s => SecretKey(s))
  given ConfigReadable[ClientId] = byString(s => ClientId(s))
  given ConfigReadable[KeyId] = byString(s => KeyId(s))
  given ConfigReadable[TeamId] = byString(s => TeamId(s))
  private def byString[T](s: String => T): ConfigReadable[T] = ConfigReadable.string.map(s)
  given ConfigReadable[ClientSecret] =
    ConfigReadable.string.map(s => ClientSecret(s))

  def parse(
    c: Config = ConfigFactory.load(LocalConf.conf).resolve().getConfig("boat")
  ): BoatConf =
    val google = c.getConfig("google")
    val ios = google.getConfig("ios")
    val web = google.getConfig("web")
    val microsoft = c.getConfig("microsoft")
    val microsoftBoat = microsoft.getConfig("boat")
    val microsoftCar = microsoft.getConfig("car")
    val push = c.getConfig("push")
    val apns = push.getConfig("apns")
    val fcm = push.getConfig("fcm")
    val mapbox = c.getConfig("mapbox")
    val ais = c.getConfig("ais")
    val result = for
      mapboxToken <- mapbox.parse[AccessToken]("token")
      aisEnabled <- ais.parse[Boolean]("enabled")
      secret <- c.parse[SecretKey]("secret")
      db <- c.parse[Conf]("db")
      iosId <- ios.parse[ClientId]("id")
      webId <- web.parse[ClientId]("id")
      webSecret <- web.parse[ClientSecret]("secret")
      microsoftBoatId <- microsoftBoat.parse[ClientId]("id")
      microsoftBoatSecret <- microsoftBoat.parse[ClientSecret]("secret")
      microsoftCarId <- microsoftCar.parse[ClientId]("id")
      microsoftCarSecret <- microsoftCar.parse[ClientSecret]("secret")
      siwa <- c.parse[SignInWithApple.Conf]("apple")
      apnsEnabled <- apns.parse[Boolean]("enabled")
      apnsPrivateKey <- apns.parse[String]("privateKey")
      apnsKeyId <- apns.parse[KeyId]("keyId")
      apnsTeamId <- apns.parse[TeamId]("teamId")
      fcmApiKey <- fcm.parse[String]("apiKey")
    yield BoatConf(
      MapboxConf(mapboxToken),
      AisAppConf(aisEnabled),
      secret,
      db,
      GoogleConf(
        AppleConf(iosId),
        WebConf(webId, webSecret)
      ),
      MicrosoftConf(
        WebConf(microsoftBoatId, microsoftBoatSecret),
        WebConf(microsoftCarId, microsoftCarSecret)
      ),
      siwa,
      PushConf(
        APNSConf(
          apnsEnabled,
          apnsPrivateKey,
          apnsKeyId,
          apnsTeamId
        ),
        FCMConf(fcmApiKey)
      )
    )
    result.fold(err => throw err, identity)
