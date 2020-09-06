package com.malliina.boat.client.server

import java.nio.charset.StandardCharsets

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import com.malliina.boat.BoatToken
import com.malliina.boat.client.Logging
import org.apache.commons.codec.digest.DigestUtils

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object WebServer {
  val log = Logging(getClass)
  val boatCharset = StandardCharsets.UTF_8
  // MD5 hash of the default password "boat"
  val defaultHash = "dd8fc45d87f91c6f9a9f43a3f355a94a"

  val changePassRoute = "init"
  val changePassUri = s"/$changePassRoute"
  val settingsPath = "settings"
  val settingsUri = s"/$settingsPath"

  def apply(host: String, port: Int, agentInstance: AgentInstance)(implicit
    as: ActorSystem,
    mat: Materializer
  ): WebServer =
    new WebServer(host, port, agentInstance)

  def hash(pass: String): String = DigestUtils.md5Hex(pass)
}

class WebServer(host: String, port: Int, agentInstance: AgentInstance)(implicit
  as: ActorSystem,
  mat: Materializer
) extends JsonSupport {
  implicit val ec = as.dispatcher

  implicit val tokenUn = Unmarshaller.strict[String, BoatToken](BoatToken.apply)
  implicit val deviceUn = Unmarshaller.strict[String, Device](Device.apply)

  import AgentHtml._
  import AgentSettings._
  import WebServer._

  val boatUser = "boat"
  val tempUser = "temp"

  val routes = concat(
    path("") {
      get {
        redirect(Uri(settingsUri), StatusCodes.SeeOther)
      }
    },
    path(settingsPath) {
      concat(
        get {
          complete(asHtml(boatForm(readConf())))
        },
        post {
          formFields(
            "host",
            "port".as[Int],
            "device".as[Device],
            "token".as[BoatToken].?,
            "enabled".as[Boolean] ? false
          ) { (host, port, device, token, enabled) =>
            val conf = BoatConf(host, port, device, token.filter(_.token.nonEmpty), enabled)
            saveAndReload(conf, agentInstance)
            redirect(Uri(settingsUri), StatusCodes.SeeOther)
          }
        }
      )
    },
    getFromResourceDirectory("assets")
  )

//  val binding = Http().newServerAt(host, port).bindFlow(routes)
  val binding = Http().bindAndHandle(routes, host, port)

  binding.foreach { _ =>
    log.info(s"Listening on $host:$port")
  }

  def stop(): Unit = {
    Await.result(binding.flatMap(_.unbind()).recover { case _ => Done }, 5.seconds)
  }
}
