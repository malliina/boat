package com.malliina.boat.db

import cats.effect.IO._
import cats.effect.{Blocker, ContextShift, IO, Resource}
import com.malliina.util.AppLogger
import com.zaxxer.hikari.HikariConfig
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.util.ExecutionContexts
import doobie.util.log.{ExecFailure, ProcessingFailure, Success}
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult

import scala.concurrent.duration.DurationInt

object DoobieDatabase {
  val log = AppLogger(getClass)

  def apply(conf: Conf, blocker: Blocker)(implicit cs: ContextShift[IO]) =
    transactor(hikariConf(conf), blocker).map { tx => new DoobieDatabase(tx) }

  def withMigrations(conf: Conf, blocker: Blocker)(implicit cs: ContextShift[IO]) =
    Resource.pure[IO, MigrateResult](migrate(conf)).flatMap { _ => apply(conf, blocker) }

  def migrate(conf: Conf): MigrateResult = {
    val flyway = Flyway.configure
      .dataSource(conf.url, conf.user, conf.pass)
      .table("flyway_schema_history2")
      .load()
    flyway.migrate()
  }

  private def hikariConf(conf: Conf): HikariConfig = {
    val hikari = new HikariConfig()
    hikari.setDriverClassName(Conf.MySQLDriver)
    hikari.setJdbcUrl(conf.url)
    hikari.setUsername(conf.user)
    hikari.setPassword(conf.pass)
    hikari.setMaxLifetime(60.seconds.toMillis)
    hikari.setMaximumPoolSize(10)
    log.info(s"Connecting to '${conf.url}'...")
    hikari
  }

  def transactor(conf: HikariConfig, blocker: Blocker)(implicit
    cs: ContextShift[IO]
  ): Resource[IO, HikariTransactor[IO]] =
    for {
      ec <- ExecutionContexts.fixedThreadPool[IO](32)
      tx <- HikariTransactor.fromHikariConfig[IO](conf, ec, blocker)
    } yield tx
}

class DoobieDatabase(tx: HikariTransactor[IO]) {
  private val log = AppLogger(getClass)
  implicit val logHandler = LogHandler {
    case Success(sql, args, exec, processing) =>
      log.info(s"OK '$sql' exec ${exec.toMillis} ms processing ${processing.toMillis} ms.")
    case ProcessingFailure(sql, args, exec, processing, failure) =>
      log.error(s"Failed '$sql' in ${exec + processing}.", failure)
    case ExecFailure(sql, args, exec, failure) =>
      log.error(s"Exec failed '$sql' in $exec.'", failure)
  }

  def run[T](io: ConnectionIO[T]): IO[T] = io.transact(tx)
}
