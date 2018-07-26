package com.malliina.boat.db

import java.sql.SQLException

import com.malliina.boat.db.DatabaseLike.log
import com.malliina.concurrent.ExecutionContexts.cached
import play.api.Logger
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.MTable
import slick.lifted.AbstractTable

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.language.higherKinds

object DatabaseLike {
  private val log = Logger(getClass)
}

abstract class DatabaseLike(val impl: JdbcProfile, val database: JdbcProfile#API#Database) {

  import impl.api._

  def tableQueries: Seq[TableQuery[_ <: Table[_]]]

  def runQuery[A, B, C[_]](query: Query[A, B, C]): Future[C[B]] =
    run(query.result)

  def runAndAwait[R](a: DBIOAction[R, NoStream, Nothing]): R = await(run(a))

  def run[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = {
    val start = System.currentTimeMillis()
    database.run(a).map { r =>
      val end = System.currentTimeMillis()
      val durationMillis = end - start
      if (durationMillis > 500) {
        log.warn(s"Database operation completed in $durationMillis ms.")
      }
      r
    }
  }

  def init(): Unit = {
    log info s"Ensuring all tables exist..."
    createIfNotExists(tableQueries: _*)
  }

  def createIfNotExists[T <: Table[_]](tables: TableQuery[T]*): Unit =
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

  def initTable[T <: Table[_]](table: TableQuery[T]): Unit = {
    val name = table.baseTableRow.tableName
    log info s"Creating table '$name'..."
    await(database.run(table.schema.create))
    log info s"Created table '$name'."
  }

  def executePlain(queries: DBIOAction[Int, NoStream, Nothing]*): Future[Seq[Int]] =
    sequentially(queries.toList)

  def sequentially(queries: List[DBIOAction[Int, NoStream, Nothing]]): Future[List[Int]] =
    queries match {
      case head :: tail =>
        database.run(head).flatMap(i => sequentially(tail).map(is => i :: is))
      case Nil =>
        Future.successful(Nil)
    }

  protected def await[T](f: Future[T]): T = Await.result(f, 20.seconds)

  def close(): Unit = database.close()
}
