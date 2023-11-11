package com.malliina.boat

import com.malliina.boat.auth.SecretKey
import com.malliina.values.Readable
import com.malliina.config.{ConfigError, ConfigReadable, InvalidValue, MissingValue}
import com.malliina.config.ConfigReadable.ConfigOps
import com.malliina.database.Conf
import com.malliina.push.apns.{KeyId, TeamId}
import com.malliina.util.FileUtils
import com.malliina.values.ErrorMessage
import com.malliina.web.{AuthConf, ClientId, ClientSecret, SignInWithApple}
import com.typesafe.config.{Config, ConfigFactory}

import java.nio.file.Path

object Env:
  def readOrElse[T: Readable](key: String, default: => T): Either[InvalidValue, T] =
    read(key).left.flatMap:
      case mv: MissingValue => Right(default)
      case iv: InvalidValue => Left(iv)

  def read[T](key: String)(using r: Readable[T]): Either[ConfigError, T] =
    sys.env
      .get(key)
      .toRight(MissingValue(key))
      .flatMap: str =>
        r.read(str).left.map(err => InvalidValue(key, err, None))

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
object AisAppConf:
  def default = AisAppConf(AppMode.fromBuild.isProd)
case class AppleConf(id: ClientId)
object AppleConf:
  val id = ClientId("497623115973-qut66ppubk4f9mpigckfkoqqoi060bge.apps.googleusercontent.com")
  val default = AppleConf(id)
case class MicrosoftConf(boat: WebConf, car: WebConf)
case class WebConf(id: ClientId, secret: ClientSecret):
  def webAuthConf = AuthConf(id, secret)
object WebConf:
  val microsoftBoatId = ClientId("d55eafcb-e3a5-4ee0-ba5c-03a6c887b6db")
  val microsoftCarId = ClientId("d1d9f5da-3d5e-4a93-9726-35f2a497a85f")
  val googleId = ClientId(
    "497623115973-c6v1e9khup8bqj41vf228o2urnv86muh.apps.googleusercontent.com"
  )
case class GoogleConf(ios: AppleConf, web: WebConf):
  def webAuthConf = AuthConf(web.id, web.secret)

case class APNSConf(enabled: Boolean, privateKey: String, keyId: KeyId, teamId: TeamId)
object APNSConf:
  val teamId = TeamId("D2T2QC36Z9")

  def push(enabled: Boolean, privateKey: String) =
    APNSConf(enabled, privateKey, KeyId("4YLFNAB5BW"), teamId)

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

  private def prodDbConf(password: String, maxPoolSize: Int) = Conf(
    "jdbc:mysql://database8-nuqmhn2cxlhle.mysql.database.azure.com:3306/boat",
    "boat",
    password,
    Conf.MySQLDriver,
    maxPoolSize = maxPoolSize,
    autoMigrate = true
  )

  private def devDbConf(password: String) = Conf(
    "jdbc:mysql://localhost:3307/boat",
    "boat",
    password,
    Conf.MySQLDriver,
    maxPoolSize = 2,
    autoMigrate = false
  )

  def parse(
    c: Config = ConfigFactory.load(LocalConf.conf).resolve().getConfig("boat")
  ): BoatConf =
    val isProdBuild = AppMode.fromBuild.isProd
    val isStaging = Env.read[String]("ENV_NAME").contains("staging")
    val google = c.getConfig("google")
    val web = google.getConfig("web")
    val microsoft = c.getConfig("microsoft")
    val microsoftBoat = microsoft.getConfig("boat")
    val microsoftCar = microsoft.getConfig("car")
    val push = c.getConfig("push")
    val apns = push.getConfig("apns")
    val fcm = push.getConfig("fcm")
    val mapbox = c.getConfig("mapbox")
    val apple = c.getConfig("apple")
    val db = c.getConfig("db")
    val result = for
      dbPass <- db.parse[String]("pass")
      mapboxToken <- mapbox.parse[AccessToken]("token")
      secret <- c.parse[SecretKey]("secret")
      webSecret <- web.parse[ClientSecret]("secret")
      microsoftBoatSecret <- microsoftBoat.parse[ClientSecret]("secret")
      microsoftCarSecret <- microsoftCar.parse[ClientSecret]("secret")
      siwaPrivateKey <- apple.parse[Path]("privateKey")
      apnsPrivateKey <- apns.parse[String]("privateKey")
      fcmApiKey <- fcm.parse[String]("apiKey")
    yield BoatConf(
      MapboxConf(mapboxToken),
      AisAppConf.default,
      secret,
      if isProdBuild then prodDbConf(dbPass, if isStaging then 2 else 10) else devDbConf(dbPass),
      GoogleConf(
        AppleConf.default,
        WebConf(WebConf.googleId, webSecret)
      ),
      MicrosoftConf(
        WebConf(WebConf.microsoftBoatId, microsoftBoatSecret),
        WebConf(WebConf.microsoftCarId, microsoftCarSecret)
      ),
      SignInWithApple.Conf.siwa(siwaPrivateKey),
      PushConf(
        APNSConf.push(isProdBuild, apnsPrivateKey),
        FCMConf(fcmApiKey)
      )
    )
    result.fold(err => throw err, identity)
