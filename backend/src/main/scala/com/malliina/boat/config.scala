package com.malliina.boat

import com.malliina.boat.auth.SecretKey
import com.malliina.boat.db.Conf
import com.malliina.push.apns.{KeyId, TeamId}
import com.malliina.web.{AuthConf, ClientId, ClientSecret}
import com.typesafe.config.ConfigFactory
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigObjectSource, ConfigReader, ConfigSource, Exported}
import pureconfig.error.{CannotConvert, ConfigReaderException, ConfigReaderFailures}
import pureconfig.generic.ProductHint
import pureconfig.generic.semiauto.deriveReader
import java.nio.file.Paths

sealed trait AppMode {
  def isProd = this == AppMode.Prod
}

object AppMode {
  case object Prod extends AppMode
  case object Dev extends AppMode
  val fromBuild = unsafe(BuildInfo.mode)

  implicit val reader: ConfigReader[AppMode] = ConfigReader.stringConfigReader.emap { s =>
    fromString(s).left.map { msg =>
      CannotConvert(s, "AppMode", msg)
    }
  }

  def unsafe(in: String): AppMode =
    fromString(in).fold(err => throw new IllegalArgumentException(err), identity)

  def fromString(in: String): Either[String, AppMode] = in match {
    case "prod" => Right(Prod)
    case "dev"  => Right(Dev)
    case other  => Left(s"Invalid mode: '$other'. Must be 'prod' or 'dev'.")
  }
}

object LocalConf {
  val homeDir = Paths.get(sys.props("user.home"))
  val appDir = homeDir.resolve(".boat")
  val localConfFile = appDir.resolve("boat.conf")
  val localConf = ConfigFactory.parseFile(localConfFile.toFile)

  def apply(): ConfigObjectSource = {
    implicit def hint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))
    ConfigObjectSource(Right(LocalConf.localConf))
      .withFallback(ConfigSource.default)
  }
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
  import pureconfig.generic.auto.exportReader
  implicit def hint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))
  private val attempt: Either[ConfigReaderFailures, BoatConf] =
    LocalConf().load[WrappedConf].map(_.boat)
  def load = attempt.fold(err => throw ConfigReaderException(err), identity)
}
