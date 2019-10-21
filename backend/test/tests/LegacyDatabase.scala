package tests

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.malliina.boat.db.{BoatSchema, Conf, InstantMariaDBProfile}
import org.scalatest.FunSuite

trait LegacyDatabase extends EmbeddedMySQL { self: FunSuite =>
  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = mat.executionContext

  lazy val ds = Conf.dataSource(conf)

  override protected def afterAll(): Unit = {
    ds.close()
    await(as.terminate())
    super.afterAll()
  }

  def boatSchema = BoatSchema(ds, InstantMariaDBProfile)
}
