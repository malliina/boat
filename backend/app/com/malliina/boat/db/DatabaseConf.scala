package com.malliina.boat.db

import com.malliina.values.ErrorMessage
import play.api.{Configuration, Mode}
import slick.jdbc.{H2Profile, JdbcProfile, MySQLProfile}

case class DatabaseConf(url: String, user: String, pass: String, driverName: String) {
  def profile: JdbcProfile = driverName match {
    case DatabaseConf.H2Driver => H2Profile
    case _ => MySQLProfile
  }
}

object DatabaseConf {
  val H2Driver = "org.h2.Driver"
  val MariaDriver = "org.mariadb.jdbc.Driver"
  val MySQLDriver = "com.mysql.jdbc.Driver"

  def apply(mode: Mode, conf: Configuration): DatabaseConf =
    if (mode == Mode.Test) inMemory
    else if (mode == Mode.Dev) fromConf(conf).getOrElse(inMemory)
    else fromConf(conf).recover(err => throw new Exception(err.message))

  def inMemory = DatabaseConf(s"jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "", "", H2Driver)

  def fromConf(conf: Configuration): Either[ErrorMessage, DatabaseConf] = {
    def read(key: String) = conf.getOptional[String](key).toRight(ErrorMessage(s"Key not found: '$key'."))

    for {
      url <- read("boat.db.url")
      user <- read("boat.db.user")
      pass <- read("boat.db.pass")
    } yield apply(url, user, pass, read("boat.db.driver").getOrElse(MySQLDriver))
  }
}
