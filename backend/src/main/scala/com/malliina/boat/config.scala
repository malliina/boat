package com.malliina.boat

import com.malliina.boat.auth.SecretKey
import com.malliina.boat.db.Conf
import com.malliina.config.ConfigReadable
import com.malliina.push.apns.{KeyId, TeamId}
import com.malliina.values.ErrorMessage
import com.malliina.web.{AuthConf, ClientId, ClientSecret}
import com.typesafe.config.{Config, ConfigFactory}

import java.nio.file.Paths

sealed trait AppMode {
  def isProd = this == AppMode.Prod
}

object AppMode {
  case object Prod extends AppMode
  case object Dev extends AppMode
  val fromBuild = unsafe(BuildInfo.mode)

  implicit val reader: ConfigReadable[AppMode] = ConfigReadable.string.emap { s =>
    fromString(s)
  }

  def unsafe(in: String): AppMode =
    fromString(in).fold(err => throw new IllegalArgumentException(err.message), identity)

  def fromString(in: String): Either[ErrorMessage, AppMode] = in match {
    case "prod" => Right(Prod)
    case "dev"  => Right(Dev)
    case other  => Left(ErrorMessage(s"Invalid mode: '$other'. Must be 'prod' or 'dev'."))
  }
}

object LocalConf {
  val homeDir = Paths.get(sys.props("user.home"))
  val appDir = homeDir.resolve(".boat")
  val localConfFile = appDir.resolve("boat.conf")
  val localConf = ConfigFactory.parseFile(localConfFile.toFile)

//  def apply(): ConfigObjectSource = {
//    implicit def hint[A]: ProductHint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))
//    ConfigObjectSource(Right(LocalConf.localConf))
//      .withFallback(ConfigSource.default)
//  }
}

case class MapboxConf(token: AccessToken)

case class AppleConf(id: ClientId)
case class WebConf(id: ClientId, secret: ClientSecret) {
  def webAuthConf = AuthConf(id, secret)
}
case class GoogleConf(ios: AppleConf, web: WebConf) {
  def webAuthConf = AuthConf(web.id, web.secret)
}
case class APNSConf(enabled: Boolean, privateKey: String, keyId: KeyId, teamId: TeamId)
case class FCMConf(apiKey: String)
case class PushConf(apns: APNSConf, fcm: FCMConf)

case class BoatConf(
  mapbox: MapboxConf,
  secret: SecretKey,
  db: Conf,
  google: GoogleConf,
  microsoft: WebConf,
  push: PushConf
)

case class WrappedConf(boat: BoatConf)

object BoatConf {
  implicit val token: ConfigReadable[AccessToken] = byString(s => AccessToken(s))
  implicit val secret: ConfigReadable[SecretKey] = byString(s => SecretKey(s))
  implicit val clientId: ConfigReadable[ClientId] = byString(s => ClientId(s))
  implicit val keyId: ConfigReadable[KeyId] = byString(s => KeyId(s))
  implicit val teamId: ConfigReadable[TeamId] = byString(s => TeamId(s))
  private def byString[T](s: String => T): ConfigReadable[T] = ConfigReadable.string.map(s)
  implicit val clientSecret: ConfigReadable[ClientSecret] =
    ConfigReadable.string.map(s => ClientSecret(s))
  implicit class ConfigOps(c: Config) extends AnyVal {
    def read[T](key: String)(implicit r: ConfigReadable[T]): Either[ErrorMessage, T] =
      r.read(key, c)
    def unsafe[T: ConfigReadable](key: String): T =
      c.read[T](key).fold(err => throw new IllegalArgumentException(err.message), identity)
  }

  def parse(
    c: Config = ConfigFactory.load(LocalConf.localConf).resolve().getConfig("boat")
  ): BoatConf = {
    val google = c.getConfig("google")
    val ios = google.getConfig("ios")
    val web = google.getConfig("web")
    val microsoft = google.getConfig("microsoft")
    val push = c.getConfig("push")
    val apns = push.getConfig("apns")
    val fcm = push.getConfig("fcm")
    BoatConf(
      MapboxConf(c.getConfig("mapbox").unsafe[AccessToken]("token")),
      c.unsafe[SecretKey]("secret"),
      parseDatabase(c.getConfig("db")),
      GoogleConf(
        AppleConf(ios.unsafe[ClientId]("id")),
        WebConf(web.unsafe[ClientId]("id"), web.unsafe[ClientSecret]("secret"))
      ),
      WebConf(microsoft.unsafe[ClientId]("id"), microsoft.unsafe[ClientSecret]("secret")),
      PushConf(
        APNSConf(
          apns.unsafe[Boolean]("enabled"),
          apns.unsafe[String]("privateKey"),
          apns.unsafe[KeyId]("keyID"),
          apns.unsafe[TeamId]("teamId")
        ),
        FCMConf(fcm.unsafe[String]("apiKey"))
      )
    )
  }

  def parseDatabase(c: Config): Conf = Conf(
    c.unsafe[String]("url"),
    c.unsafe[String]("user"),
    c.unsafe[String]("pass"),
    c.unsafe[String]("driver"),
    c.unsafe[Int]("maxPoolSize")
  )

//  import pureconfig.generic.auto.exportReader
//  implicit def hint[A]: ProductHint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))
//  private val attempt: Either[ConfigReaderFailures, BoatConf] =
//    loadAs[WrappedConf].map(_.boat)
//  def loadAs[T: ConfigReader] = LocalConf().load[T]
//  def load = attempt.fold(err => throw ConfigReaderException(err), identity)
}
