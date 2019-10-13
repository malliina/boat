package com.malliina.boat.db

import play.api.Configuration

case class Conf(url: String, user: String, pass: String, driver: String)

object Conf {
  val UrlKey = "boat.db.url"
  val UserKey = "boat.db.user"
  val PassKey = "boat.db.pass"
  val DriverKey = "boat.db.driver"
  val MySQLDriver = "com.mysql.jdbc.Driver"
  val DefaultDriver = MySQLDriver

  def fromEnvOrFail() =
    fromEnv().fold(err => throw new Exception(err), identity)

  def fromConf(conf: Configuration) = from(key => conf.getOptional[String](key))

  def fromEnv() = from(key => sys.env.get(key).orElse(sys.props.get(key)))

  def from(readKey: String => Option[String]) = {
    def read(key: String) =
      readKey(key)
        .toRight(s"Key missing: '$key'.")

    for {
      url <- read(UrlKey)
      user <- read(UserKey)
      pass <- read(PassKey)
    } yield Conf(url, user, pass, read(DriverKey).getOrElse(DefaultDriver))
  }
}
