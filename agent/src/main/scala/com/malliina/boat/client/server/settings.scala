package com.malliina.boat.client.server

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.malliina.boat.BoatToken
import com.malliina.boat.client.server.WebServer.{boatCharset, defaultHash, hash, log}
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.Try

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

  def confDir =
    sys.props.get("conf.dir").map(s => Paths.get(s)).getOrElse(Files.createTempDirectory("boat-"))

  def readPass: String =
    if (Files.exists(passFile))
      Files.readAllLines(passFile).asScala.headOption.getOrElse(defaultHash)
    else
      defaultHash

  def savePass(pass: String): Unit = save(hash(pass), passFile)

  def readConf(): BoatConf =
    if (Files.exists(confFile)) {
      Try(
        new String(Files.readAllBytes(confFile), StandardCharsets.UTF_8).parseJson
          .convertTo[BoatConf]).getOrElse(BoatConf.empty)
    } else {
      BoatConf.empty
    }

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
