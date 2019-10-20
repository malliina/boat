package com.malliina.boat.db

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.malliina.boat.{Language, SimpleUserInfo}
import com.malliina.boat.http.{BoatQuery, Limits, TimeRange}
import com.malliina.values.Username
import org.scalatest.BeforeAndAfterAll
import tests.BaseSuite

class PerformanceTests extends BaseSuite with BeforeAndAfterAll {
  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = mat.executionContext

  val conf = Conf(
    "jdbc:mysql://localhost:3306/boat?useSSL=false",
    "",
    "",
    Conf.MySQLDriver
  )
  val newConf = Conf(
    "jdbc:mysql://localhost:3306/boat?useSSL=false",
    "",
    "",
    Conf.MySQLDriver
  )

  ignore("performance") {
    val newDb = BoatDatabase(as, conf)
    val db = BoatSchema(newDb.ds, conf.driver)
    db.initApp()
    val old = TracksDatabase(db, ec)
    val tdb = NewTracksDatabase(newDb)
    benchmark("Old", old)
    benchmark("New", tdb)
    benchmark("Old", old)
    benchmark("New", tdb)
    benchmark("Old", old)
    benchmark("New", tdb)
  }

  def benchmark(name: String, db: TracksSource) = {
    val q = BoatQuery(Limits.default, TimeRange.none, Nil, Nil, None, None, newest = true)
    def history = db.history(SimpleUserInfo(Username("mle"), Language.english), q)
    val start = System.currentTimeMillis()
    val result = await(history)
    val end = System.currentTimeMillis()
    println(s"$name took ${end - start} ms.")
  }

  override protected def afterAll(): Unit = {
    await(as.terminate())
  }
}
