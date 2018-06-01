package com.malliina.boat.client.server

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.directives.Credentials.Provided
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.{ActorMaterializer, Materializer}
import com.malliina.boat.BoatToken
import com.malliina.boat.client.server.WebServer.{boatCharset, defaultHash, hash, log}
import com.malliina.boat.client.{BoatAgent, Logging}
import org.apache.commons.codec.digest.DigestUtils
import scalatags.Text
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Try

object AgentInstance {
  def apply(initial: BoatConf)(implicit as: ActorSystem, mat: Materializer): AgentInstance =
    new AgentInstance(initial)
}

class AgentInstance(initialConf: BoatConf)(implicit as: ActorSystem, mat: Materializer) {
  private var conf = initialConf
  private var agent = BoatAgent.prod(conf)

  def updateIfNecessary(newConf: BoatConf): Boolean = synchronized {
    if (newConf != conf) {
      conf = newConf
      val oldAgent = agent
      oldAgent.close()
      val newAgent = BoatAgent.prod(newConf)
      agent = newAgent
      if (newConf.enabled) {
        newAgent.connect()
      }
      true
    } else {
      false
    }
  }
}

object AgentWebServer {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("agent-system")
    implicit val materializer = ActorMaterializer()

    val agentManager = new AgentInstance(AgentSettings.readConf())
    WebServer("0.0.0.0", 8080, agentManager)
  }
}

case class BoatConf(host: String, port: Int, token: Option[BoatToken], enabled: Boolean) {
  def describe = s"$host:$port-$enabled"
}

object BoatConf {
  val empty = BoatConf("", 0, None, enabled = false)

  def anon(host: String, port: Int) = BoatConf(host, port, None, enabled = true)
}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  implicit object tokenFormat extends JsonFormat[BoatToken] {
    override def write(obj: BoatToken): JsValue = JsString(obj.token)

    override def read(json: JsValue): BoatToken = BoatToken(StringJsonFormat.read(json))
  }

  implicit val confFormat = jsonFormat4(BoatConf.apply)
}

object AgentSettings extends JsonSupport {

  import spray.json.enrichString

  val passFile = file("pass.md5")
  val confFile = file("boat.conf")

  def file(name: String) = confDir.resolve(name)

  def confDir = sys.props.get("conf.dir").map(s => Paths.get(s)).getOrElse(Files.createTempDirectory("boat-"))

  def readPass: String =
    if (Files.exists(passFile)) Files.readAllLines(passFile).asScala.headOption.getOrElse(defaultHash)
    else defaultHash

  def savePass(pass: String): Unit = save(hash(pass), passFile)

  def readConf(): BoatConf =
    if (Files.exists(confFile))
      Try(new String(Files.readAllBytes(confFile), StandardCharsets.UTF_8).parseJson.convertTo[BoatConf]).getOrElse(BoatConf.empty)
    else
      BoatConf.empty

  def saveConf(conf: BoatConf): Unit = save(confFormat.write(conf).prettyPrint, confFile)

  def saveAndReload(conf: BoatConf, instance: AgentInstance): Boolean = {
    saveConf(conf)
    instance.updateIfNecessary(conf)
  }

  def save(content: String, to: Path): Unit = {
    Files.write(to, content.getBytes(boatCharset))
    log.info(s"Wrote $to.")
  }
}

object WebServer {
  val log = Logging(getClass)
  val boatCharset = StandardCharsets.UTF_8
  // MD5 hash of the default password "boat"
  val defaultHash = "dd8fc45d87f91c6f9a9f43a3f355a94a"

  val changePassRoute = "init"
  val changePassUri = s"/$changePassRoute"
  val settingsPath = "settings"
  val settingsUri = s"/$settingsPath"

  def apply(host: String, port: Int, agentInstance: AgentInstance)(implicit as: ActorSystem, mat: Materializer): WebServer =
    new WebServer(host, port, agentInstance)

  def hash(pass: String): String = DigestUtils.md5Hex(pass)
}

class WebServer(host: String, port: Int, agentInstance: AgentInstance)(implicit as: ActorSystem, mat: Materializer) extends JsonSupport {
  implicit val ec = as.dispatcher

  implicit val tokenUn = Unmarshaller.strict[String, BoatToken](BoatToken.apply)

  import AgentHtml._
  import AgentSettings._
  import WebServer._

  val boatUser = "boat"
  val tempUser = "temp"

  val routes = concat(
    path("") {
      authenticateBasic(realm = "Boat Agent", initialAuth) { user =>
        get {
          val dest = if (user == tempUser) changePassRoute else settingsPath
          redirect(Uri(s"/$dest"), StatusCodes.SeeOther)
        }
      }
    },
    path(changePassRoute) {
      authenticateBasic(realm = "Boat Agent", initialAuth) { user =>
        concat(
          get {
            complete(asHtml(changePassForm))
          },
          post {
            formFields('pass) { pass =>
              savePass(pass)
              redirect(Uri(settingsUri), StatusCodes.SeeOther)
            }
          }
        )
      }
    },
    path(settingsPath) {
      authenticateBasic(realm = "Boat Agent", passAuth) { user =>
        concat(
          get {
            complete(asHtml(boatForm(readConf())))
          },
          post {
            formFields('host, 'port.as[Int], 'token.as[BoatToken].?, 'enabled.as[Boolean] ? false) { (host, port, token, enabled) =>
              saveAndReload(BoatConf(host, port, token.filter(_.token.nonEmpty), enabled), agentInstance)
              redirect(Uri(settingsUri), StatusCodes.SeeOther)
            }
          }
        )
      }
    },
    getFromResourceDirectory("assets")
  )

  def initialAuth(credentials: Credentials): Option[String] =
    credentials match {
      case p@Credentials.Provided(user) if isValid(p) => Option(user)
      case Credentials.Missing if defaultHash == readPass => Option(tempUser)
      case _ => None
    }

  def passAuth(credentials: Credentials): Option[String] =
    credentials match {
      case p@Credentials.Provided(user) if isValid(p) => Option(user)
      case _ => None
    }

  def isValid(provided: Provided): Boolean =
    defaultHash != readPass && provided.identifier == boatUser && provided.verify(readPass, WebServer.hash)

  val binding = Http().bindAndHandle(routes, host, port)

  binding.foreach { _ =>
    log.info(s"Listening on $host:$port")
  }

  def stop(): Unit = {
    Await.result(binding.flatMap(_.unbind()).recover { case _ => Done }, 5.seconds)
  }
}

object AgentHtml {

  import scalatags.Text.all._

  val empty = stringFrag("")

  def boatForm(conf: BoatConf) = form(action := WebServer.settingsUri, method := "post")(
    h2("Settings"),
    div(`class` := "form-field")(
      label(`for` := "host")("Host"),
      input(`type` := "text", name := "host", id := "host", value := conf.host)
    ),
    div(`class` := "form-field")(
      label(`for` := "port")("Port"),
      input(`type` := "number", name := "port", id := "port", value := conf.port)
    ),
    div(`class` := "form-field")(
      label(`for` := "token")("Token"),
      input(`type` := "text", name := "token", id := "token", conf.token.map(v => value := v.token).getOrElse(empty))
    ),
    div(`class` := "form-field")(
      label(`for` := "enabled")("Enabled"),
      input(`type` := "checkbox", name := "enabled", id := "enabled", if (conf.enabled) checked else empty)
    ),
    div(`class` := "form-field")(
      button(`type` := "submit")("Save")
    )
  )

  def changePassForm = form(action := WebServer.changePassUri, method := "post")(
    h2("Set password"),
    div(`class` := "form-field")(
      label(`for` := "pass")("Password"),
      input(`type` := "password", name := "pass", id := "pass")
    ),
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
}