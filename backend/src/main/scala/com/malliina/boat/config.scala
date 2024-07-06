package com.malliina.boat

import cats.effect.kernel.Sync
import com.malliina.boat.auth.SecretKey
import com.malliina.config.{ConfigError, ConfigNode, ConfigReadable, Env}
import com.malliina.database.Conf
import com.malliina.push.apns.{KeyId, TeamId}
import com.malliina.util.FileUtils
import com.malliina.values.{ErrorMessage, Readable}
import com.malliina.web.{AuthConf, ClientId, ClientSecret, SignInWithApple}

import java.nio.file.Path

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
  val localConf = ConfigNode.default(localConfFile)
  val conf = if isProd then ConfigNode.load("application-prod.conf") else localConf

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

case class APNSConf(enabled: Boolean, privateKey: Path, keyId: KeyId, teamId: TeamId)
object APNSConf:
  val teamId = TeamId("D2T2QC36Z9")

  def push(enabled: Boolean, privateKey: Path) =
    APNSConf(enabled, privateKey, KeyId("4YLFNAB5BW"), teamId)

case class FCMConf(apiKey: String)

case class PushConf(apns: APNSConf, fcm: FCMConf)

case class BoatConf(
  isTest: Boolean,
  isProdBuild: Boolean,
  mapbox: MapboxConf,
  ais: AisAppConf,
  secret: SecretKey,
  db: Conf,
  google: GoogleConf,
  microsoft: MicrosoftConf,
  apple: SignInWithApple.Conf,
  push: PushConf
):
  def isFull = isProdBuild || isTest

object BoatConf:
  def parseF[F[_]: Sync] = Sync[F].fromEither(parseBoat())

  def parseBoat(): Either[ConfigError, BoatConf] =
    for
      boat <- LocalConf.conf.parse[ConfigNode]("boat")
      conf <- parse(boat)
    yield conf

  private def parse(
    c: ConfigNode
  ): Either[ConfigError, BoatConf] =
    val isProdBuild = AppMode.fromBuild.isProd
    val envName = Env.read[String]("ENV_NAME")
    val isStaging = envName.contains("staging")
    val isProd = envName.contains("prod")
    for
      dbPass <- c.parse[String]("db.pass")
      mapboxToken <- c.parse[AccessToken]("mapbox.token")
      secret <-
        if BuildInfo.isProd then c.parse[SecretKey]("secret")
        else c.opt[SecretKey]("secret").map(_.getOrElse(SecretKey.dev))
      webSecret <- c.parse[ClientSecret]("google.web.secret")
      microsoft <- c.parse[ConfigNode]("microsoft")
      microsoftBoatSecret <- microsoft.parse[ClientSecret]("boat.secret")
      microsoftCarSecret <- microsoft.parse[ClientSecret]("car.secret")
      siwaPrivateKey <- c.parse[Path]("apple.privateKey")
      apnsPrivateKey <- c.parse[Path]("push.apns.privateKey")
      fcmApiKey <- c.parse[String]("push.fcm.apiKey")
    yield BoatConf(
      isTest = false,
      isProdBuild,
      MapboxConf(mapboxToken),
      AisAppConf.default,
      secret,
      if isProdBuild then prodDbConf(dbPass, if isStaging then 2 else 10)
      else devDatabaseConf(dbPass),
      GoogleConf(
        AppleConf.default,
        WebConf(WebConf.googleId, webSecret)
      ),
      MicrosoftConf(
        WebConf(WebConf.microsoftBoatId, microsoftBoatSecret),
        WebConf(WebConf.microsoftCarId, microsoftCarSecret)
      ),
      SignInWithApple.Conf.siwa(isProd, siwaPrivateKey),
      PushConf(
        APNSConf.push(isProd, apnsPrivateKey),
        FCMConf(fcmApiKey)
      )
    )

  private def prodDbConf(password: String, maxPoolSize: Int) = Conf(
    "jdbc:mysql://localhost:3306/boat",
    "boat",
    password,
    Conf.MySQLDriver,
    maxPoolSize = maxPoolSize,
    autoMigrate = true,
    schemaTable = "flyway_schema_history2"
  )

  private def devDatabaseConf(password: String) = Conf(
    "jdbc:mysql://localhost:3307/boat",
    "boat",
    password,
    Conf.MySQLDriver,
    maxPoolSize = 2,
    autoMigrate = false,
    schemaTable = "flyway_schema_history2"
  )
