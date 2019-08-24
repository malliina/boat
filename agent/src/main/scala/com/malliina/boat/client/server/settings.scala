package com.malliina.boat.client.server

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.malliina.boat.BoatToken
import com.malliina.boat.client.server.Device.BoatDevice
import com.malliina.boat.client.server.WebServer.{boatCharset, defaultHash, hash, log}
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.Try

sealed abstract class Device(val name: String)

object Device extends DefaultJsonProtocol {
  val all = Seq(BoatDevice, GpsDevice)
  val default = BoatDevice

  case object GpsDevice extends Device("gps") {
    val watchCommand =
      """?WATCH={"enable":true,"json":false,"nmea":true,"raw":0,"scaled":false,"timing":false,"split24":false,"pps":false}"""
  }
  case object BoatDevice extends Device("boat")

  def apply(s: String): Device = all.find(_.name == s.toLowerCase).getOrElse(default)

  implicit object json extends JsonFormat[Device] {
    override def read(json: JsValue) = apply(json.convertTo[String])
    override def write(obj: Device) = JsString(obj.name)
  }
}

case class BoatConfOld(host: String, port: Int, token: Option[BoatToken], enabled: Boolean) {
  def toConf = BoatConf(host, port, BoatDevice, token, enabled)
}

case class BoatConf(host: String,
                    port: Int,
                    device: Device,
                    token: Option[BoatToken],
                    enabled: Boolean) {
  def describe = s"$host:$port-$enabled"
}

object BoatConf {
  val empty = BoatConf("", 0, Device.default, None, enabled = false)

  def anon(host: String, port: Int) = BoatConf(host, port, Device.default, None, enabled = true)
}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  implicit object tokenFormat extends JsonFormat[BoatToken] {
    override def write(obj: BoatToken): JsValue = JsString(obj.token)

    override def read(json: JsValue): BoatToken = BoatToken(StringJsonFormat.read(json))
  }

  implicit val oldConfFormat = jsonFormat4(BoatConfOld.apply)
  implicit val confFormat = jsonFormat5(BoatConf.apply)
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
      // If reading the conf fails, attempts to read the old format and then save it as new
      Try(new String(Files.readAllBytes(confFile), StandardCharsets.UTF_8).parseJson).flatMap {
        json =>
          Try(json.convertTo[BoatConf]).orElse {
            Try {
              val converted = json.convertTo[BoatConfOld].toConf
              saveConf(converted)
              converted
            }
          }
      }.getOrElse(BoatConf.empty)
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
