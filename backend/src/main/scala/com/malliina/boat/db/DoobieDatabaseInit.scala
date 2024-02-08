package com.malliina.boat.db

import cats.effect.{Async, Resource, Sync}
import com.malliina.database.{Conf, DoobieDatabase}
import com.malliina.util.AppLogger
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult

// TODO support table name customization in com.malliina %% database, then remove this file and use DoobieDatabase.scala directly
object DoobieDatabaseInit2:
  val log = AppLogger(getClass)

  def init[F[_]: Async](conf: Conf): Resource[F, DoobieDatabase[F]] =
    if conf.autoMigrate then withMigrations[F](conf) else DoobieDatabase.default[F](conf)

  def fast[F[_]: Async](conf: Conf): Resource[F, DoobieDatabase[F]] =
    Resource.pure(DoobieDatabase.fast(conf))

  def withMigrations[F[_]: Async](conf: Conf): Resource[F, DoobieDatabase[F]] =
    Resource.eval[F, MigrateResult](migrate(conf)).flatMap(_ => DoobieDatabase.default(conf))

  private def migrate[F[_]: Sync](conf: Conf): F[MigrateResult] = Sync[F].delay:
    val flyway = Flyway.configure
      .dataSource(conf.url, conf.user, conf.pass)
      .table("flyway_schema_history2")
      .load()
    flyway.migrate()
