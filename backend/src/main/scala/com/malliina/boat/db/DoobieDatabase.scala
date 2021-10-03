package com.malliina.boat.db

import cats.effect.IO.*
import cats.effect.kernel.Resource
import cats.effect.IO
import com.malliina.util.AppLogger
import com.zaxxer.hikari.HikariConfig
import doobie.*
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.util.ExecutionContexts
import doobie.util.log.{ExecFailure, ProcessingFailure, Success}
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult

import scala.concurrent.duration.DurationInt

object DoobieDatabase:
  val log = AppLogger(getClass)

  def apply(conf: Conf): Resource[IO, DoobieDatabase] =
    transactor(hikariConf(conf)).map { tx => new DoobieDatabase(tx) }

  def withMigrations(conf: Conf) =
    Resource.eval[IO, MigrateResult](migrate(conf)).flatMap { _ => apply(conf) }

  def migrate(conf: Conf): IO[MigrateResult] = IO {
    val flyway = Flyway.configure
      .dataSource(conf.url, conf.user, conf.pass)
      .table("flyway_schema_history2")
      .load()
    flyway.migrate()
  }

  private def hikariConf(conf: Conf): HikariConfig =
    val hikari = new HikariConfig()
    hikari.setDriverClassName(Conf.MySQLDriver)
    hikari.setJdbcUrl(conf.url)
    hikari.setUsername(conf.user)
    hikari.setPassword(conf.pass)
    hikari.setMaxLifetime(60.seconds.toMillis)
    hikari.setMaximumPoolSize(10)
    log.info(s"Connecting to '${conf.url}'...")
    hikari

  def transactor(conf: HikariConfig): Resource[IO, HikariTransactor[IO]] =
    for
      ec <- ExecutionContexts.fixedThreadPool[IO](32)
      tx <- HikariTransactor.fromHikariConfig[IO](conf, ec)
    yield tx

class DoobieDatabase(tx: HikariTransactor[IO]):
  private val log = AppLogger(getClass)

  implicit val logHandler: LogHandler = LogHandler {
    case Success(sql, args, exec, processing) =>
      val msg = s"OK '$sql' exec ${exec.toMillis} ms processing ${processing.toMillis} ms."
      if exec > 1.second then log.info(msg) else log.debug(msg)
    case ProcessingFailure(sql, args, exec, processing, failure) =>
      log.error(s"Failed '$sql' in ${exec + processing}.", failure)
    case ExecFailure(sql, args, exec, failure) =>
      log.error(s"Exec failed '$sql' in $exec.'", failure)
  }

  def run[T](io: ConnectionIO[T]): IO[T] = io.transact(tx)
