package tests

import ch.vorburger.mariadb4j.{DB, DBConfigurationBuilder}
import com.malliina.boat.db.Conf
import org.scalatest.{BeforeAndAfterAll, FunSuite, Suite}

trait EmbeddedMySQL extends BeforeAndAfterAll { self: FunSuite =>
  private val dbConfig =
    DBConfigurationBuilder
      .newBuilder()
      .setDeletingTemporaryBaseAndDataDirsOnShutdown(true)
  lazy val db = DB.newEmbeddedDB(dbConfig.build())
  lazy val conf = {
    db.start()
    Conf(dbConfig.getURL("test"), "root", "", Conf.MySQLDriver)
  }

  // This hack ensures that beforeAll and afterAll is run even when all tests are ignored,
  // ensuring resources are cleaned up in all situations.
  test("database lifecycle") {
    assert(1 === 1)
  }

  override protected def beforeAll(): Unit = {
    val c = conf
  }

  override protected def afterAll(): Unit = {
    db.stop()
  }
}
