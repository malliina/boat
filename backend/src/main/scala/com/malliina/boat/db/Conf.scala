package com.malliina.boat.db

case class Conf(url: String, user: String, pass: String, driver: String, maxPoolSize: Int)

object Conf {
  val UrlKey = "url"
  val UserKey = "user"
  val PassKey = "pass"
  val DriverKey = "driver"
  val MySQLDriver = "com.mysql.jdbc.Driver"
  val DefaultDriver = MySQLDriver

//  def fromConf(conf: Configuration) = fromDatabaseConf(conf.get[Configuration]("boat.db"))
//
//  def fromDatabaseConf(conf: Configuration) = {
//    def read(key: String) =
//      conf
//        .getOptional[String](key)
//        .toRight(s"Key missing: '$key'.")
//
//    for {
//      url <- read(UrlKey)
//      user <- read(UserKey)
//      pass <- read(PassKey)
//    } yield Conf(url, user, pass, read(DriverKey).getOrElse(DefaultDriver), 10)
//  }
}
