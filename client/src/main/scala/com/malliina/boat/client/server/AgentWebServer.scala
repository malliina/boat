package com.malliina.boat.client.server

import java.nio.charset.StandardCharsets

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.directives.Credentials.Provided
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.{ActorMaterializer, Materializer}
import com.malliina.boat.BoatToken
import com.malliina.boat.client.{BoatAgent, Logging}
import org.apache.commons.codec.digest.DigestUtils

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object AgentInstance {
  def apply(initial: BoatConf)(implicit as: ActorSystem, mat: Materializer): AgentInstance =
    new AgentInstance(initial)
}

class AgentInstance(initialConf: BoatConf)(implicit as: ActorSystem, mat: Materializer) {
  private var conf = initialConf
  private var agent = BoatAgent.prod(conf)
  if (initialConf.enabled) {
    agent.connect()
  }

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

  val binding = Http().bindAndHandle(routes, host, port)

  binding.foreach { _ =>
    log.info(s"Listening on $host:$port")
  }

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

  def stop(): Unit = {
    Await.result(binding.flatMap(_.unbind()).recover { case _ => Done }, 5.seconds)
  }
}
