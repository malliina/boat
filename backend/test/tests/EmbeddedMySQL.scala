package tests

import ch.vorburger.mariadb4j.{DB, DBConfigurationBuilder}
import com.malliina.boat.db.{BoatDatabase, Conf}

import scala.concurrent.ExecutionContext

trait EmbeddedMySQL extends AsyncSuite {
  private val dbConfig =
    DBConfigurationBuilder
      .newBuilder()
      .setDeletingTemporaryBaseAndDataDirsOnShutdown(true)
  lazy val db = DB.newEmbeddedDB(dbConfig.build())
  lazy val conf: Conf = {
    db.start()
    Conf(dbConfig.getURL("test"), "root", "", Conf.MySQLDriver, isMariaDb = true)
  }

  def testDatabase(ec: ExecutionContext) = BoatDatabase.withMigrations(as, conf)

  // This hack ensures that beforeAll and afterAll is run even when all tests are ignored,
  // ensuring resources are cleaned up in all situations.
  test("database lifecycle") {
    assert(1 === 1)
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val c = conf
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    db.stop()
  }
}
