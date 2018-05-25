package com.malliina.boat.it

import com.malliina.boat._
import com.malliina.boat.client.{HttpUtil, JsonSocket, KeyValue}
import com.malliina.http.FullUrl
import com.malliina.logstreams.client.CustomSSLSocketFactory
import com.malliina.play.models.Password
import com.malliina.util.Utils
import controllers.BoatController
import org.scalatest.FunSuite
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponents
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Call
import tests.OneServerPerSuite2

import scala.concurrent.{Await, Future}

abstract class TestAppSuite extends ServerSuite(new AppComponents(_))

abstract class ServerSuite[T <: BuiltInComponents](build: Context => T)
  extends BaseSuite
    with OneServerPerSuite2[T] {
  override def createComponents(context: Context) = build(context)
}

abstract class BaseSuite extends FunSuite {
  val reverse = controllers.routes.BoatController

  def await[T](f: Future[T]): T = Await.result(f, 3.seconds)
}

abstract class BoatTests extends TestAppSuite with BoatSockets {
  def withBoat[T](boat: BoatName, creds: Option[Creds] = None)(code: JsonSocket => T) =
    withWebSocket(reverse.boats(), boat, creds, _ => ())(code)

  def withViewer[T](onJson: JsValue => Any, creds: Option[Creds] = None)(code: JsonSocket => T) =
    withWebSocket(reverse.updates(), BoatNames.random(), creds, onJson)(code)

  def withWebSocket[T](path: Call, boat: BoatName, creds: Option[Creds], onJson: JsValue => Any)(code: TestSocket => T) = {
    val wsUrl = FullUrl("ws", s"localhost:$port", path.toString)
    withSocket(wsUrl, boat, onJson, creds)(code)
  }
}

trait BoatSockets {
  this: BaseSuite =>

  def withSocket[T](url: FullUrl, boat: BoatName, onJson: JsValue => Any, creds: Option[Creds] = None)(code: TestSocket => T) =
    Utils.using(new TestSocket(url, boat, creds, onJson)) { client =>
      await(client.initialConnection)
      code(client)
    }

  class TestSocket(url: FullUrl, boat: BoatName, creds: Option[Creds], onJson: JsValue => Any) extends JsonSocket(
    url,
    CustomSSLSocketFactory.forHost("boat.malliina.com"),
    //    SSLUtils.trustAllSslContext().getSocketFactory,
    creds.map(c => KeyValue(HttpUtil.Authorization, HttpUtil.authorizationValue(c.user, c.pass.pass))).toSeq ++
      Seq(KeyValue(BoatController.BoatNameHeader, boat.name))
  ) {
    override def onText(message: String): Unit = onJson(Json.parse(message))
  }

}

case class Creds(user: User, pass: Password)
