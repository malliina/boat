package com.malliina.boat.client.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.malliina.boat.BoatToken
import org.scalatest.FunSuite

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}

class WebServerTests extends FunSuite {
  implicit val system = ActorSystem("agent-system")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  test("json") {
    object json extends JsonSupport

    assert(json.tokenFormat.write(BoatToken("demo")).compactPrint === "\"demo\"")
  }

  test("get") {
    val server = WebServer("127.0.0.1", 0, AgentInstance(BoatConf.empty))
    val testServer = Http().bindAndHandle(server.routes, "127.0.0.1", 0)
    val binding = await(testServer)

    def urlTo(path: String) = s"http://${binding.localAddress.getHostString}:${binding.localAddress.getPort}$path"

    val res = await(Http().singleRequest(HttpRequest(method = HttpMethods.GET, uri = urlTo(WebServer.settingsUri))))
    assert(res.status === StatusCodes.OK)
  }

  test("initial pass hash") {
    assert(WebServer.hash("boat") === WebServer.defaultHash)
  }

  def await[T](f: Future[T], duration: Duration = 3.seconds): T = Await.result(f, duration)
}