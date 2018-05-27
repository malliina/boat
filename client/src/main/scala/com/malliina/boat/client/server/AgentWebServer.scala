package com.malliina.boat.client.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.ActorMaterializer
import com.malliina.boat.BoatToken
import com.malliina.boat.client.Logging
import com.malliina.boat.client.server.WebServer.log
import scalatags.Text
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

object AgentWebServer {
  def main(args: Array[String]): Unit = {
    WebServer("localhost", 8080)
  }
}

case class DataIn(a: String, b: String)

case class BoatConf(host: String, port: Int, token: BoatToken, enabled: Boolean) {
  def describe = s"$host:$port-$enabled"
}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  implicit object tokenFormat extends JsonFormat[BoatToken] {
    override def write(obj: BoatToken): JsValue = JsString(obj.token)

    override def read(json: JsValue): BoatToken = BoatToken(StringJsonFormat.read(json))
  }

  implicit val dataInFormat = jsonFormat2(DataIn)
  implicit val confFormat = jsonFormat4(BoatConf)
}

object WebServer {
  val log = Logging(getClass)

  def apply(host: String, port: Int): WebServer =
    new WebServer(host, port)
}

class WebServer(host: String, port: Int) extends JsonSupport {

  import scalatags.Text.all._

  implicit val system = ActorSystem("agent-system")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  implicit val tokenUn = Unmarshaller.strict[String, BoatToken](BoatToken.apply)

  val routes =
    path("") {
      get {
        complete(asHtml(boatForm))
      } ~ post {
        formFields('host, 'port.as[Int], 'token.as[BoatToken], 'enabled.as[Boolean] ? false) { (host, port, token, enabled) =>
          val conf = BoatConf(host, port, token, enabled)
          val asString = confFormat.write(conf).compactPrint
          // todo save conf
          complete(StatusCodes.Accepted, s"Saved $asString.")
        }
      }
    } ~
      path("hello") {
        get {
          complete(asHtml(h1("Say hello to akka-http and Scalatags")))
        } ~
          post {
            entity(as[DataIn]) { data =>
              complete(StatusCodes.Created, s"done ${data.a}")
            }
          }
      } ~ getFromResourceDirectory("assets")

  val binding = Http().bindAndHandle(routes, host, port)

  binding.map { b =>
    log.info(s"Listening on $host:$port")
  }

  def boatForm = form(action := "/", method := "post")(
    h2("Settings"),
    div(`class` := "form-field")(
      label(`for` := "host")("Host"),
      input(`type` := "text", name := "host", id := "host")
    ),
    div(`class` := "form-field")(
      label(`for` := "port")("Port"),
      input(`type` := "number", name := "port", id := "port")
    ),
    div(`class` := "form-field")(
      label(`for` := "token")("Token"),
      input(`type` := "text", name := "token", id := "token")
    ),
    div(`class` := "form-field")(
      label(`for` := "enabled")("Enabled"),
      input(`type` := "checkbox", name := "enabled", id := "enabled")),
    div(`class` := "form-field")(
      button(`type` := "submit")("Save")
    )
  )

  def asHtml(content: Text.TypedTag[String]): HttpEntity.Strict = {
    val payload = html(
      head(link(rel := "stylesheet", href := "/css/boat.css")),
      body(content)
    )
    HttpEntity(ContentTypes.`text/html(UTF-8)`, payload.render)
  }

  def stop(): Unit = binding.foreach(_.unbind())
}
