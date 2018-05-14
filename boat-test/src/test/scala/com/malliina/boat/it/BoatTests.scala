package com.malliina.boat.it

import com.malliina.boat._
import com.malliina.boat.client.JsonSocket
import com.malliina.http.FullUrl
import com.malliina.security.SSLUtils
import com.malliina.util.Utils
import controllers.BoatController
import org.scalatest.FunSuite
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponents
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Call
import tests.OneServerPerSuite2

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

abstract class TestAppSuite extends ServerSuite(new AppComponents(_))

abstract class ServerSuite[T <: BuiltInComponents](build: Context => T)
  extends BaseSuite
    with OneServerPerSuite2[T] {
  override def createComponents(context: Context) = build(context)
}

abstract class BaseSuite extends FunSuite {
  val reverse = controllers.routes.BoatController

  def await[T](f: Future[T]): T = Await.result(f, 30.seconds)
}

abstract class BoatTests extends TestAppSuite with BoatSockets {
  def withBoat[T](boat: BoatName)(code: JsonSocket => T) =
    withWebSocket(reverse.boats(), boat, _ => ())(code)

  def withViewer[T](onJson: JsValue => Any)(code: JsonSocket => T) =
    withWebSocket(reverse.updates(), BoatNames.random(), onJson)(code)

  def withWebSocket[T](path: Call, boat: BoatName, onJson: JsValue => Any)(code: TestSocket => T) = {
    val wsUrl = FullUrl("ws", s"localhost:$port", path.toString)
    withSocket(wsUrl, boat, onJson)(code)
  }
}

trait BoatSockets {
  this: BaseSuite =>
  //  val testUser = Username("test")
  //  val testPass = Password("pass")

  def withSocket[T](url: FullUrl, boat: BoatName, onJson: JsValue => Any)(code: TestSocket => T) = {
    Utils.using(new TestSocket(url, boat, onJson)) { client =>
      await(client.initialConnection)
      code(client)
    }
  }

  class TestSocket(wsUri: FullUrl, boat: BoatName, onJson: JsValue => Any) extends JsonSocket(
    wsUri,
    SSLUtils.trustAllSslContext().getSocketFactory,
    Seq(
      //      HttpUtil.Authorization -> HttpUtil.authorizationValue(testUser.name, testPass.pass),
      BoatController.BoatNameHeader -> boat.name)
  ) {
    override def onText(message: String): Unit = onJson(Json.parse(message))
  }

}
