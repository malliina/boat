package com.malliina.boat.db

import java.sql.{PreparedStatement, ResultSet, Timestamp}
import java.time.Instant

import com.malliina.values.ErrorMessage
import play.api.{Configuration, Mode}
import slick.ast.FieldSymbol
import slick.jdbc.{H2Profile, JdbcProfile, MySQLProfile}

trait BoatJdbcProfile extends JdbcProfile {
  def lastId: String
  def date: String
  def distance: String
}

object BoatJdbcProfile {
  def apply(driverName: String): BoatJdbcProfile = driverName match {
    case DatabaseConf.H2Driver => InstantH2Profile
    case _                     => InstantMySQLProfile
  }
}

object InstantMySQLProfile extends BoatJdbcProfile with MySQLProfile {
  override val lastId = "last_insert_id"
  override val date = "date"
  override val distance = "ST_Distance_Sphere"
  override val columnTypes = new ProperJdbcTypes

  class ProperJdbcTypes extends super.JdbcTypes {
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

object InstantH2Profile extends BoatJdbcProfile with H2Profile {
  override val lastId = "scope_identity"
  override val date = "truncate"
  // The H2 distance function is wrong, but I just want something that compiles for H2
  override val distance = "ST_MaxDistance"
  override val columnTypes = new ProperJdbcTypes

  class ProperJdbcTypes extends super.JdbcTypes {
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

case class DatabaseConf(url: String, user: String, pass: String, driverName: String) {
  def profile = BoatJdbcProfile(driverName)
}

object DatabaseConf {
  val H2Driver = "org.h2.Driver"
  val MariaDriver = "org.mariadb.jdbc.Driver"
  val MySQLDriver = "com.mysql.jdbc.Driver"

  def apply(mode: Mode, conf: Configuration): DatabaseConf =
    if (mode == Mode.Test) inMemory
    else if (mode == Mode.Dev) fromConf(conf).getOrElse(inMemory)
    else fromConf(conf).recover(err => throw new Exception(err.message))

  def inMemory = inMemoryNamed("test")

  def inMemoryNamed(name: String) =
    DatabaseConf(s"jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false", "", "", H2Driver)

  def fromConf(conf: Configuration): Either[ErrorMessage, DatabaseConf] = {
    //    conf.get[Configuration]("boat.db")
    def read(key: String) =
      conf.getOptional[String](key).toRight(ErrorMessage(s"Key not found: '$key'."))
    for {
      url <- read("boat.db.url")
      user <- read("boat.db.user")
      pass <- read("boat.db.pass")
    } yield apply(url, user, pass, read("boat.db.driver").getOrElse(MySQLDriver))
  }
}
