package com.malliina.boat.db

import cats.effect.IO._
import cats.effect.{Blocker, ContextShift, IO, Resource}
import com.malliina.boat.JoinedBoat
import doobie._
import doobie.implicits._
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor.Aux
import javax.sql.DataSource

import scala.concurrent.{ExecutionContext, Future}

object DoobieTracksDatabase {
  def apply(ds: DataSource, ec: ExecutionContext) = new DoobieTracksDatabase(ds)(ec)
}

class DoobieTracksDatabase(ds: DataSource)(implicit ec: ExecutionContext) {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  private val tx: Resource[IO, Aux[IO, DataSource]] = for {
    ec <- ExecutionContexts.fixedThreadPool[IO](32) // our connect EC
    be <- Blocker[IO] // our blocking EC
  } yield Transactor.fromDataSource[IO](ds, ec, be)

  val boatsQuery =
    sql"""select b.id, b.name, b.token, u.id, u.user, u.email, u.language
         from boats b, users u 
         where b.owner = u.id"""
  val boatsView = boatsQuery.query[JoinedBoat].to[List]

  def hm: Future[Int] = run {
    sql"select 42".query[Int].unique
  }

  protected def run[T](io: ConnectionIO[T]): Future[T] =
    tx.use(r => io.transact(r)).unsafeToFuture()
}
