package tests

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.malliina.boat.db.{BoatSchema, Conf, InstantMariaDBProfile}
import org.scalatest.{BeforeAndAfterAll, FunSuite}

trait LegacyDatabase extends BeforeAndAfterAll { self: FunSuite =>
  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = mat.executionContext

  lazy val conf = TestComponents.startTestDatabase()
  lazy val ds = Conf.dataSource(conf)

  override protected def afterAll(): Unit = {
    ds.close()
    await(as.terminate())
  }

  def boatSchema = BoatSchema(ds, InstantMariaDBProfile)
}
