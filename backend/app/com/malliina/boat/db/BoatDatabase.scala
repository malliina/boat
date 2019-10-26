package com.malliina.boat.db

import java.sql.{Timestamp, Types}
import java.time.Instant

import akka.actor.ActorSystem
import com.malliina.boat.{Coord, Usernames}
import com.malliina.boat.db.BoatDatabase.log
import com.malliina.measure.DistanceM
import com.zaxxer.hikari.HikariDataSource
import io.getquill.{MySQLDialect, MysqlJdbcContext, NamingStrategy, SnakeCase}
import org.flywaydb.core.Flyway
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object BoatDatabase {
  private val log = Logger(getClass)

  def withMigrations(as: ActorSystem, conf: Conf): BoatDatabase[SnakeCase] = {
    val flyway =
      Flyway.configure.dataSource(conf.url, conf.user, conf.pass).load()
    flyway.migrate()
    apply(as, conf)
  }

  private def apply(as: ActorSystem, dbConf: Conf): BoatDatabase[SnakeCase] = {
    val pool = as.dispatchers.lookup("contexts.database")
    apply(Conf.dataSource(dbConf), pool, dbConf.isMariaDb)
  }

  private def apply(
      ds: HikariDataSource,
      ec: ExecutionContext,
      isMariaDb: Boolean
  ): BoatDatabase[SnakeCase] = {
    // Seems like the MysqlEscape NamingStrategy is buggy, but can be dangerous to live without it also
    // TODO: Perhaps roll my own escaping, and submit PR to quill
    new BoatDatabase[SnakeCase](ds, SnakeCase, ec, isMariaDb)
  }

  def fail(message: String): Nothing = throw new Exception(message)
}

class BoatDatabase[N <: NamingStrategy](
    val ds: HikariDataSource,
    naming: N,
    val ec: ExecutionContext,
    isMariaDb: Boolean
) extends MysqlJdbcContext(naming, ds)
    with NewMappings
    with Quotes[MySQLDialect, N]
    with StatsQuotes[MySQLDialect, N] {
  implicit val ie: Encoder[Instant] = encoder(
    Types.TIMESTAMP,
    (idx, value, row) => row.setTimestamp(idx, new Timestamp(value.toEpochMilli))
  )
  private val distanceFunctionName = if (isMariaDb) "ST_Distance" else "ST_Distance_Sphere"
  val selectDistance = quote { (from: Coord, to: Coord) =>
    infix"SELECT #$distanceFunctionName($from,$to)".as[Query[DistanceM]]
  }

  def transactionally[T](name: String)(io: IO[T, _]): Future[Result[T]] =
    performAsync(name)(io.transactional)
  def performAsync[T](name: String)(io: IO[T, _]): Future[Result[T]] = Future(perform(name, io))(ec)

  def perform[T](name: String, io: IO[T, _]): Result[T] = {
    val start = System.currentTimeMillis()
    val result = performIO(io)
    val end = System.currentTimeMillis()
    log.warn(s"$name completed in ${end - start} ms.")
    result
  }

  def first[T, E <: Effect](io: IO[Seq[T], E], onEmpty: => String): IO[T, E] =
    io.flatMap { ts =>
      ts.headOption.map { t =>
        IO.successful(t)
      }.getOrElse { IO.failed(new Exception(onEmpty)) }
    }

  def fail(message: String): Nothing = BoatDatabase.fail(message)
}
