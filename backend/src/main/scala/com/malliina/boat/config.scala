package com.malliina.boat

import com.malliina.boat.auth.SecretKey
import com.malliina.boat.db.Conf
import com.malliina.push.apns.{KeyId, TeamId}
import com.malliina.web.{AuthConf, ClientId, ClientSecret}
import com.typesafe.config.ConfigFactory
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigObjectSource, ConfigReader, ConfigSource}
import pureconfig.error.{CannotConvert, ConfigReaderException, ConfigReaderFailures}
import pureconfig.generic.ProductHint

import java.nio.file.Paths

sealed trait AppMode {
  def isProd = this == AppMode.Prod
}

object AppMode {
  case object Prod extends AppMode
  case object Dev extends AppMode

  implicit val reader: ConfigReader[AppMode] = ConfigReader.stringConfigReader.emap {
    case "prod" => Right(Prod)
    case "dev"  => Right(Dev)
    case other  => Left(CannotConvert(other, "AppMode", "Must be 'prod' or 'dev'."))
  }
}

object LocalConf {
  val homeDir = Paths.get(sys.props("user.home"))
  val appDir = homeDir.resolve(".boat")
  val localConfFile = appDir.resolve("boat.conf")
  val localConf = ConfigFactory.parseFile(localConfFile.toFile)
}

case class MapboxConf(token: AccessToken)
case class AppleConf(id: ClientId)
case class WebConf(id: ClientId, secret: ClientSecret)
case class GoogleConf(ios: AppleConf, web: WebConf) {
  def webAuthConf = AuthConf(web.id, web.secret)
}
case class APNSConf(enabled: Boolean, privateKey: String, keyId: KeyId, teamId: TeamId)
case class FCMConf(apiKey: String)
case class PushConf(apns: APNSConf, fcm: FCMConf)

case class BoatConf(
  mode: AppMode,
  mapbox: MapboxConf,
  secret: SecretKey,
  db: Conf,
  google: GoogleConf,
  push: PushConf
)

case class WrappedConf(boat: BoatConf)

object BoatConf {
  implicit def camelCaseConf[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))
  import pureconfig.generic.auto.exportReader
  val attempt: Either[ConfigReaderFailures, BoatConf] =
    ConfigObjectSource(Right(LocalConf.localConf))
      .withFallback(ConfigSource.default)
      .load[WrappedConf]
      .map(_.boat)

  def load = attempt.fold(err => throw ConfigReaderException(err), identity)
}
