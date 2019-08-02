package com.malliina.boat.db

import java.sql.SQLException

import com.malliina.boat.db.DatabaseClient.log
import com.malliina.concurrent.Execution.cached
import play.api.Logger
import slick.jdbc.meta.MTable
import slick.lifted.AbstractTable

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}
import scala.language.higherKinds

trait JdbcComponent {
  // "Stable identifier required" error in DatabaseComponent if def instead of val
  val jdbc: BoatJdbcProfile
  val api = jdbc.api
}

object DatabaseClient {
  private val log = Logger(getClass)
}

trait DatabaseClient extends JdbcComponent {
  import api._

  def database: jdbc.backend.DatabaseDef

  def runAndAwait[R](a: DBIOAction[R, NoStream, Nothing], duration: Duration = 20.seconds): R =
    await(run(a), duration)

  def runQuery[A, B, C[_]](query: Query[A, B, C]): Future[C[B]] =
    run(query.result)

  def run[R](a: DBIOAction[R, NoStream, Nothing],
             label: String = "Database operation"): Future[R] = {
    val start = System.currentTimeMillis()
    database.run(a).map { r =>
      val end = System.currentTimeMillis()
      val durationMillis = end - start
      if (durationMillis > 500) {
        DatabaseClient.log.warn(s"$label completed in $durationMillis ms.")
      }
      r
    }
  }

  def action[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] =
    action("Database operation")(a)

  def transaction[R](a: DBIOAction[R, NoStream, Effect.All]): Future[R] =
    action("Database operation")(a.transactionally)

  def action[R](label: String)(a: DBIOAction[R, NoStream, Nothing]): Future[R] =
    run(a, label)

  def initTable[T <: Table[_]](table: TableQuery[T]): Unit = {
    val name = table.baseTableRow.tableName
    log info s"Creating table '$name'..."
    await(database.run(table.schema.create))
    log info s"Created table '$name'."
  }

  def createIfNotExists[T <: jdbc.Table[_]](tables: TableQuery[T]*): Unit =
    tables.reverse.filter(t => !exists(t)).foreach(t => initTable(t))

  def exists[T <: AbstractTable[_]](table: TableQuery[T]): Boolean = {
    val tableName = table.baseTableRow.tableName
    try {
      val future = database.run(MTable.getTables(tableName))
      val ts = await(future)
      ts.nonEmpty
    } catch {
      case sqle: SQLException =>
        log.error(s"Unable to verify table: $tableName", sqle)
        false
    }
  }

  protected def await[T](f: Future[T], duration: Duration = 20.seconds): T =
    Await.result(f, duration)

  def close(): Unit = database.close()
}
