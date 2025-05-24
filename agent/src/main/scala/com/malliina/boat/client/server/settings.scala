package com.malliina.boat.client.server

import com.comcast.ip4s.*
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
  given Codec[Device] = Codec.from(
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
  import com.malliina.boat.GPSInfo.{hostCodec, portCodec}
  given Codec[BoatConfOld] = deriveCodec[BoatConfOld]

case class BoatConf(
  host: Host,
  port: Port,
  device: Device,
  token: Option[BoatToken],
  enabled: Boolean
):
  def describe = s"$host:$port-$enabled"

object BoatConf:
  import com.malliina.boat.GPSInfo.{hostCodec, portCodec}
  given Codec[BoatConf] = deriveCodec[BoatConf]

  def anon(host: Host, port: Port) = BoatConf(host, port, Device.default, None, enabled = true)

object AgentSettings:
  private val passFile = file("pass.md5")
  private val confFile = file("boat.conf")

  def file(name: String) = confDir.resolve(name)

  private def confDir =
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
      jsonContent
        .flatMap: jsonResult =>
          jsonResult.flatMap: json =>
            json
              .as[BoatConf]
              .left
              .map(_ => "JSON error.")
              .left
              .flatMap: _ =>
                json
                  .as[BoatConfOld]
                  .left
                  .map(_ => s"Old JSON error.")
                  .map: old =>
                    val converted = old.toConf
                    saveConf(converted)
                    converted
        .left
        .map(_ => "Error.")
    else Left("No conf")

  private def saveConf(conf: BoatConf): Unit = save(conf.asJson.spaces2, confFile)

  def saveAndReload[F[_]](conf: BoatConf, instance: AgentInstance[F]): F[Boolean] =
    saveConf(conf)
    instance.updateIfNecessary(conf)

  private def save(content: String, to: Path): Unit =
    Files.write(to, content.getBytes(boatCharset))
    log.info(s"Wrote $to.")
