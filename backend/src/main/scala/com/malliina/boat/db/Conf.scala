package com.malliina.boat.db

case class Conf(
  url: String,
  user: String,
  pass: String,
  driver: String,
  maxPoolSize: Int,
  autoMigrate: Boolean
)

object Conf:
  val UrlKey = "url"
  val UserKey = "user"
  val PassKey = "pass"
  val DriverKey = "driver"
  val MySQLDriver = "com.mysql.cj.jdbc.Driver"
  val DefaultDriver = MySQLDriver
