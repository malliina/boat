package com.malliina.boat

import com.malliina.boat.auth.SecretKey
import com.malliina.boat.db.Conf
import com.malliina.config.{ConfigOps, ConfigReadable}
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

  implicit val reader: ConfigReadable[AppMode] = ConfigReadable.string.emap { s =>
    fromString(s)
  }

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
  microsoft: WebConf,
  apple: SignInWithApple.Conf,
  push: PushConf
)

object BoatConf:
  implicit val token: ConfigReadable[AccessToken] = byString(s => AccessToken(s))
  implicit val secret: ConfigReadable[SecretKey] = byString(s => SecretKey(s))
  implicit val clientId: ConfigReadable[ClientId] = byString(s => ClientId(s))
  implicit val keyId: ConfigReadable[KeyId] = byString(s => KeyId(s))
  implicit val teamId: ConfigReadable[TeamId] = byString(s => TeamId(s))
  private def byString[T](s: String => T): ConfigReadable[T] = ConfigReadable.string.map(s)
  implicit val clientSecret: ConfigReadable[ClientSecret] =
    ConfigReadable.string.map(s => ClientSecret(s))

  def parse(
    c: Config = ConfigFactory.load(LocalConf.conf).resolve().getConfig("boat")
  ): BoatConf =
    val google = c.getConfig("google")
    val ios = google.getConfig("ios")
    val web = google.getConfig("web")
    val microsoft = c.getConfig("microsoft")
    val push = c.getConfig("push")
    val apns = push.getConfig("apns")
    val fcm = push.getConfig("fcm")
    BoatConf(
      MapboxConf(c.getConfig("mapbox").unsafe[AccessToken]("token")),
      AisAppConf(c.getConfig("ais").unsafe[Boolean]("enabled")),
      c.unsafe[SecretKey]("secret"),
      parseDatabase(c.getConfig("db")),
      GoogleConf(
        AppleConf(ios.unsafe[ClientId]("id")),
        WebConf(web.unsafe[ClientId]("id"), web.unsafe[ClientSecret]("secret"))
      ),
      WebConf(microsoft.unsafe[ClientId]("id"), microsoft.unsafe[ClientSecret]("secret")),
      c.unsafe[SignInWithApple.Conf]("apple"),
      PushConf(
        APNSConf(
          apns.unsafe[Boolean]("enabled"),
          apns.unsafe[String]("privateKey"),
          apns.unsafe[KeyId]("keyId"),
          apns.unsafe[TeamId]("teamId")
        ),
        FCMConf(fcm.unsafe[String]("apiKey"))
      )
    )

  def parseDatabase(c: Config): Conf = Conf(
    c.unsafe[String]("url"),
    c.unsafe[String]("user"),
    c.unsafe[String]("pass"),
    c.unsafe[String]("driver"),
    c.unsafe[Int]("maxPoolSize"),
    c.unsafe[Boolean]("autoMigrate")
  )
