package com.malliina.boat.db

import java.sql.{PreparedStatement, ResultSet, Timestamp}
import java.time.Instant

import com.malliina.values.ErrorMessage
import play.api.{Configuration, Mode}
import slick.ast.FieldSymbol
import slick.jdbc.{H2Profile, JdbcProfile, MySQLProfile}

object InstantMySQLProfile extends JdbcProfile with MySQLProfile {
  override val columnTypes = new JdbcTypes

  class JdbcTypes extends super.JdbcTypes {
    override val instantType = new InstantJdbcType {
      override def sqlTypeName(sym: Option[FieldSymbol]) = "TIMESTAMP(3)"
      override def setValue(v: Instant, p: PreparedStatement, idx: Int): Unit =
        p.setTimestamp(idx, Timestamp.from(v))
      override def getValue(r: ResultSet, idx: Int): Instant =
        Option(r.getTimestamp(idx)).map(_.toInstant).orNull
      override def updateValue(v: Instant, r: ResultSet, idx: Int): Unit =
        r.updateTimestamp(idx, Timestamp.from(v))
      override def valueToSQLLiteral(value: Instant): String = s"'${Timestamp.from(value)}'"
    }
  }
}

object InstantH2Profile extends JdbcProfile with H2Profile {
  override val columnTypes = new JdbcTypes

  class JdbcTypes extends super.JdbcTypes {
    override val instantType = new InstantJdbcType {
      override def sqlTypeName(sym: Option[FieldSymbol]) = "TIMESTAMP(3)"
      override def setValue(v: Instant, p: PreparedStatement, idx: Int): Unit =
        p.setTimestamp(idx, Timestamp.from(v))
      override def getValue(r: ResultSet, idx: Int): Instant =
        Option(r.getTimestamp(idx)).map(_.toInstant).orNull
      override def updateValue(v: Instant, r: ResultSet, idx: Int): Unit =
        r.updateTimestamp(idx, Timestamp.from(v))
      override def valueToSQLLiteral(value: Instant): String = s"'${Timestamp.from(value)}'"
    }
  }
}

case class ProfileConf(profile: JdbcProfile, lastIdFunc: String)

object ProfileConf {
  val h2 = ProfileConf(InstantH2Profile, "scope_identity")
  val mysql = ProfileConf(InstantMySQLProfile, "last_insert_id")

  def apply(driverName: String): ProfileConf = driverName match {
    case DatabaseConf.H2Driver => h2
    case _                     => mysql
  }
}

case class DatabaseConf(url: String, user: String, pass: String, driverName: String) {
  def profileConf = ProfileConf(driverName)

  def lastIdFunc = profileConf.lastIdFunc
}

object DatabaseConf {
  val H2Driver = "org.h2.Driver"
  val MariaDriver = "org.mariadb.jdbc.Driver"
  val MySQLDriver = "com.mysql.jdbc.Driver"

  def apply(mode: Mode, conf: Configuration): DatabaseConf =
    if (mode == Mode.Test) inMemory
    else if (mode == Mode.Dev) fromConf(conf).getOrElse(inMemory)
    else fromConf(conf).recover(err => throw new Exception(err.message))

  def inMemory =
    DatabaseConf(s"jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false", "", "", H2Driver)

  def fromConf(conf: Configuration): Either[ErrorMessage, DatabaseConf] = {
    def read(key: String) =
      conf.getOptional[String](key).toRight(ErrorMessage(s"Key not found: '$key'."))

    for {
      url <- read("boat.db.url")
      user <- read("boat.db.user")
      pass <- read("boat.db.pass")
    } yield apply(url, user, pass, read("boat.db.driver").getOrElse(MySQLDriver))
  }
}
