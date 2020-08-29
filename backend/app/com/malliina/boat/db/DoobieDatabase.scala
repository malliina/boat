package com.malliina.boat.db

import cats.effect.IO._
import cats.effect.{Blocker, ContextShift, IO, Resource}
import com.zaxxer.hikari.HikariDataSource
import doobie._
import doobie.implicits._
import doobie.util.ExecutionContexts
import doobie.util.log.{ExecFailure, ProcessingFailure, Success}
import doobie.util.transactor.Transactor.Aux
import javax.sql.DataSource
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object DoobieDatabase {
  def apply(ds: HikariDataSource, ec: ExecutionContext): DoobieDatabase = new DoobieDatabase(ds, ec)
}

class DoobieDatabase(ds: HikariDataSource, val ec: ExecutionContext) {
  private val log = Logger(getClass)

  private implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  private val tx: Resource[IO, Aux[IO, DataSource]] = for {
    ec <- ExecutionContexts.fixedThreadPool[IO](32) // our connect EC
    be <- Blocker[IO] // our blocking EC
  } yield Transactor.fromDataSource[IO](ds, ec, be)
  implicit val logHandler = LogHandler {
    case Success(sql, args, exec, processing) =>
      log.info(s"OK '$sql' exec ${exec.toMillis} ms processing ${processing.toMillis} ms.")
    case ProcessingFailure(sql, args, exec, processing, failure) =>
      log.error(s"Failed '$sql' in ${exec + processing}.", failure)
    case ExecFailure(sql, args, exec, failure) =>
      log.error(s"Exec failed '$sql' in $exec.'", failure)
  }

  def run[T](io: ConnectionIO[T]): Future[T] =
    tx.use(r => io.transact(r)).unsafeToFuture()

  def close(): Unit = ds.close()
}
