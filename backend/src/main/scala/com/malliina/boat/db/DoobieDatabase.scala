package com.malliina.boat.db

import cats.effect.kernel.Resource
import cats.effect.{Async, Sync}
import com.malliina.util.AppLogger
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
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

  def resource[F[_]: Async](conf: => Conf): Resource[F, DoobieDatabase[F]] =
    transactor[F](hikariConf(conf)).map { tx => DoobieDatabase(tx) }

  def init[F[_]: Async](conf: Conf): Resource[F, DoobieDatabase[F]] =
    if conf.autoMigrate then withMigrations[F](conf) else resource[F](conf)

  def withMigrations[F[_]: Async](conf: Conf): Resource[F, DoobieDatabase[F]] =
    Resource.eval[F, MigrateResult](migrate(conf)).flatMap { _ => resource(conf) }

  private def migrate[F[_]: Sync](conf: Conf): F[MigrateResult] = Sync[F].delay {
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
    hikari.setMaximumPoolSize(conf.maxPoolSize)
    log.info(s"Connecting to '${conf.url}'...")
    hikari

  private def transactor[F[_]: Async](conf: HikariConfig): Resource[F, HikariTransactor[F]] =
    for
      ec <- ExecutionContexts.fixedThreadPool[F](32)
      tx <- HikariTransactor.fromHikariConfig[F](conf, ec)
    yield tx

class DoobieDatabase[F[_]: Async](tx: HikariTransactor[F]):
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

  def run[T](io: ConnectionIO[T]): F[T] = io.transact(tx)
