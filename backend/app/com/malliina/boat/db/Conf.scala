package com.malliina.boat.db

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import play.api.{Configuration, Logger}

case class Conf(url: String, user: String, pass: String, driver: String)

object Conf {
  private val log = Logger(getClass)

  val UrlKey = "boat.db.url"
  val UserKey = "boat.db.user"
  val PassKey = "boat.db.pass"
  val DriverKey = "boat.db.driver"
  val MySQLDriver = "com.mysql.jdbc.Driver"
  val DefaultDriver = MySQLDriver

  def fromEnvOrFail(): Conf =
    fromEnv().fold(err => throw new Exception(err), identity)

  def fromConf(conf: Configuration) = from(key => conf.getOptional[String](key))

  def fromEnv() = from(key => sys.env.get(key).orElse(sys.props.get(key)))

  def from(readKey: String => Option[String]) = {
    def read(key: String) = readKey(key).toRight(s"Key missing: '$key'.")

    for {
      url <- read(UrlKey)
      user <- read(UserKey)
      pass <- read(PassKey)
    } yield Conf(url, user, pass, read(DriverKey).getOrElse(DefaultDriver))
  }

  def dataSource(conf: Conf): HikariDataSource = {
    val hikari = new HikariConfig()
    hikari.setDriverClassName(Conf.MySQLDriver)
    hikari.setJdbcUrl(conf.url)
    hikari.setUsername(conf.user)
    hikari.setPassword(conf.pass)
    log info s"Connecting to '${conf.url}'..."
    new HikariDataSource(hikari)
  }
}
