package com.malliina.boat.db

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import play.api.{Configuration, Logger}

import scala.concurrent.duration.DurationInt

case class Conf(url: String, user: String, pass: String, driver: String)

object Conf {
  private val log = Logger(getClass)

  val UrlKey = "url"
  val UserKey = "user"
  val PassKey = "pass"
  val DriverKey = "driver"
  val MySQLDriver = "com.mysql.jdbc.Driver"
  val DefaultDriver = MySQLDriver

  def fromConf(conf: Configuration) = {
    def read(key: String) =
      conf
        .get[Configuration]("boat.db")
        .getOptional[String](key)
        .toRight(s"Key missing: 'boat.db.$key'.")

    for {
      url <- read(UrlKey)
      user <- read(UserKey)
      pass <- read(PassKey)
    } yield Conf(url, user, pass, read(DriverKey).getOrElse(DefaultDriver))
  }

  def dataSource(conf: Conf): HikariDataSource = {
    val hikari = new HikariConfig()
    hikari.setDriverClassName(conf.driver)
    hikari.setJdbcUrl(conf.url)
    hikari.setUsername(conf.user)
    hikari.setPassword(conf.pass)
    // Azure complains if using default values
    hikari.setMaxLifetime(60.seconds.toMillis)
    // Why is this set to 5?
    hikari.setMaximumPoolSize(5)
    log.info(s"Connecting to '${conf.url}'...")
    new HikariDataSource(hikari)
  }
}
