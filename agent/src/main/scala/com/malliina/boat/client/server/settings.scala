package com.malliina.boat.client.server

import com.malliina.boat.BoatToken
import com.malliina.boat.client.server.Device.BoatDevice
import com.malliina.boat.client.server.WebServer.{boatCharset, defaultHash, hash, log}
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax.EncoderOps
import io.circe.{Codec, Decoder, Encoder, parser}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.Try

sealed abstract class Device(val name: String):
  override def toString = name

object Device:
  implicit val codec: Codec[Device] = Codec.from(
    Decoder.decodeString.map(apply),
    Encoder.encodeString.contramap(_.name)
  )
  val all = Seq(BoatDevice, GpsDevice)
  val default = BoatDevice

  case object GpsDevice extends Device("gps"):
    val watchCommand =
      """?WATCH={"enable":true,"json":false,"nmea":true,"raw":0,"scaled":false,"timing":false,"split24":false,"pps":false}"""
  case object BoatDevice extends Device("boat")

  def apply(s: String): Device = all.find(_.name == s.toLowerCase).getOrElse(default)

case class BoatConfOld(host: String, port: Int, token: Option[BoatToken], enabled: Boolean):
  def toConf = BoatConf(host, port, BoatDevice, token, enabled)

object BoatConfOld:
  implicit val codec: Codec[BoatConfOld] = deriveCodec[BoatConfOld]

case class BoatConf(
  host: String,
  port: Int,
  device: Device,
  token: Option[BoatToken],
  enabled: Boolean
):
  def describe = s"$host:$port-$enabled"

object BoatConf:
  implicit val json: Codec[BoatConf] = deriveCodec[BoatConf]
  val empty = BoatConf("", 0, Device.default, None, enabled = false)

  def anon(host: String, port: Int) = BoatConf(host, port, Device.default, None, enabled = true)

object AgentSettings:
  val passFile = file("pass.md5")
  val confFile = file("boat.conf")

  def file(name: String) = confDir.resolve(name)

  def confDir =
    sys.props.get("conf.dir").map(s => Paths.get(s)).getOrElse(Files.createTempDirectory("boat-"))

  def readPass: String =
    if Files.exists(passFile) then
      Files.readAllLines(passFile).asScala.headOption.getOrElse(defaultHash)
    else defaultHash

  def savePass(pass: String): Unit = save(hash(pass), passFile)

  def readConf(): BoatConf =
    if Files.exists(confFile) then
      // If reading the conf fails, attempts to read the old format and then save it as new
      Try(
        parser.parse(new String(Files.readAllBytes(confFile), StandardCharsets.UTF_8))
      ).toEither.flatMap { jsonResult =>
        jsonResult.flatMap { json =>
          json.as[BoatConf].left.flatMap { err =>
            json.as[BoatConfOld].map { old =>
              val converted = old.toConf
              saveConf(converted)
              converted
            }
          }
        }
      }.getOrElse(BoatConf.empty)
    else BoatConf.empty

  def saveConf(conf: BoatConf): Unit = save(conf.asJson.spaces2, confFile)

  def saveAndReload(conf: BoatConf, instance: AgentInstance): Boolean =
    saveConf(conf)
    instance.updateIfNecessary(conf)

  def save(content: String, to: Path): Unit =
    Files.write(to, content.getBytes(boatCharset))
    log.info(s"Wrote $to.")
