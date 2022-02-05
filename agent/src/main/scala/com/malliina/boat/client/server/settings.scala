package com.malliina.boat.client.server

import cats.effect.IO
import com.malliina.boat.BoatToken
import com.malliina.boat.client.server.Device.BoatDevice
import com.malliina.boat.client.server.WebServer.{boatCharset, defaultHash, hash, log}
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax.EncoderOps
import io.circe.{Codec, Decoder, Encoder, parser}
import com.comcast.ip4s.*
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

case class BoatConfOld(host: Host, port: Port, token: Option[BoatToken], enabled: Boolean):
  def toConf = BoatConf(host, port, BoatDevice, token, enabled)

object BoatConfOld:
  import BoatConf.{hostCodec, portCodec}
  implicit val codec: Codec[BoatConfOld] = deriveCodec[BoatConfOld]

case class BoatConf(
  host: Host,
  port: Port,
  device: Device,
  token: Option[BoatToken],
  enabled: Boolean
):
  def describe = s"$host:$port-$enabled"

object BoatConf:
  implicit val hostCodec: Codec[Host] = Codec.from(
    Decoder.decodeString.emap(s => Host.fromString(s).toRight(s"Invalid host: '$s'.")),
    Encoder.encodeString.contramap[Host](h => Host.show.show(h))
  )
  implicit val portCodec: Codec[Port] = Codec.from(
    Decoder.decodeInt.emap(i => Port.fromInt(i).toRight(s"Invalid port: '$i'.")),
    Encoder.encodeInt.contramap[Port](p => p.value)
  )

  implicit val json: Codec[BoatConf] = deriveCodec[BoatConf]
//  val empty = BoatConf("", 0, Device.default, None, enabled = false)

  def anon(host: Host, port: Port) = BoatConf(host, port, Device.default, None, enabled = true)

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

  def readConf(): Either[String, BoatConf] =
    if Files.exists(confFile) then
      // If reading the conf fails, attempts to read the old format and then save it as new
      val jsonContent = Try(
        parser.parse(new String(Files.readAllBytes(confFile), StandardCharsets.UTF_8))
      ).toEither.left.map(_ => s"Cannot read '$confFile'.")
      jsonContent.flatMap { jsonResult =>
        jsonResult.flatMap { json =>
          json.as[BoatConf].left.map(_ => "JSON error.").left.flatMap { err =>
            json
              .as[BoatConfOld]
              .left
              .map(_ => s"Old JSON error.")
              .map { old =>
                val converted = old.toConf
                saveConf(converted)
                converted
              }
          }
        }
      }.left.map(_ => "Error.")
    else Left("No conf")

  def saveConf(conf: BoatConf): Unit = save(conf.asJson.spaces2, confFile)

  def saveAndReload(conf: BoatConf, instance: AgentInstance): IO[Boolean] =
    saveConf(conf)
    instance.updateIfNecessary(conf)

  def save(content: String, to: Path): Unit =
    Files.write(to, content.getBytes(boatCharset))
    log.info(s"Wrote $to.")
