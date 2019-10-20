package com.malliina.boat.db

import akka.actor.ActorSystem
import com.malliina.boat.db.BoatDatabase.log
import com.zaxxer.hikari.HikariDataSource
import io.getquill.{MySQLDialect, MysqlJdbcContext, NamingStrategy, SnakeCase}
import org.flywaydb.core.Flyway
import play.api.Logger

import scala.concurrent.ExecutionContext

object BoatDatabase {
  private val log = Logger(getClass)

  def mysqlFromEnvOrFail(as: ActorSystem): BoatDatabase[SnakeCase] =
    withMigrations(as, Conf.fromEnvOrFail())

  def withMigrations(as: ActorSystem, conf: Conf): BoatDatabase[SnakeCase] = {
    val flyway =
      Flyway.configure.dataSource(conf.url, conf.user, conf.pass).load()
    flyway.migrate()
    apply(as, conf)
  }

  def apply(as: ActorSystem, dbConf: Conf): BoatDatabase[SnakeCase] = {
    val pool = as.dispatchers.lookup("contexts.database")
    apply(Conf.dataSource(dbConf), pool)
  }

  def apply(ds: HikariDataSource, ec: ExecutionContext): BoatDatabase[SnakeCase] = {
    // Seems like the MysqlEscape NamingStrategy is buggy, but can be dangerous to live without it also
    // TODO: Perhaps roll my own escaping, and submit PR to quill
    new BoatDatabase[SnakeCase](ds, SnakeCase, ec)
  }

  def fail(message: String): Nothing = throw new Exception(message)
}
class BoatDatabase[N <: NamingStrategy](
    val ds: HikariDataSource,
    naming: N,
    val ec: ExecutionContext
) extends MysqlJdbcContext(naming, ds)
    with NewMappings
    with Quotes[MySQLDialect, N]
    with StatsQuotes[MySQLDialect, N] {

  def perform[T](name: String, io: IO[T, _]): Result[T] = {
    val start = System.currentTimeMillis()
    val result = performIO(io)
    val end = System.currentTimeMillis()
    log.warn(s"$name completed in ${end - start} ms.")
    result
  }
}
