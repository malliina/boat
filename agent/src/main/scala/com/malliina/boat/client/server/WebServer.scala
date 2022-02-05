package com.malliina.boat.client.server

import cats.effect.IO
import com.malliina.boat.client.TcpClient
import com.malliina.boat.client.TcpClient.{port, host}
import com.malliina.boat.client.server.AgentHtml.{asHtml, boatForm}
import com.malliina.boat.client.server.AgentSettings.{readConf, saveAndReload}
import com.malliina.boat.client.server.WebServer.settingsUri
import com.malliina.boat.{BoatToken, Errors, Readable}
import com.malliina.util.AppLogger
import io.circe.syntax.EncoderOps
import org.apache.commons.codec.digest.DigestUtils
import org.http4s.circe.CirceInstances
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.*
import com.comcast.ip4s.{Host, Port}
import org.http4s.CacheDirective.*
import org.http4s.headers.{Accept, Location, `Cache-Control`, `WWW-Authenticate`}
import java.nio.charset.StandardCharsets

trait AppImplicits
  extends syntax.AllSyntax
  with Http4sDsl[IO]
  with CirceInstances
  with ScalatagsEncoder

object WebServer:
  val log = AppLogger(getClass)
  val boatCharset = StandardCharsets.UTF_8
  // MD5 hash of the default password "boat"
  val defaultHash = "dd8fc45d87f91c6f9a9f43a3f355a94a"

  val changePassRoute = "init"
  val changePassUri = uri"/init"
  val settingsPath = "settings"
  val settingsUri = uri"/settings"

  def hash(pass: String): String = DigestUtils.md5Hex(pass)

class WebServer(agentInstance: AgentInstance) extends AppImplicits:
  val noCache = `Cache-Control`(`no-cache`(), `no-store`, `must-revalidate`)
  val boatUser = "boat"
  val tempUser = "temp"

  val routes = HttpRoutes.of[IO] {
    case GET -> Root =>
      SeeOther(Location(settingsUri))
    case GET -> Root / "settings" =>
      Ok(asHtml(boatForm(readConf().toOption)), noCache)
    case req @ GET -> Root / "settings" =>
      parseForm(req, readForm).flatMap { boatConf =>
        saveAndReload(boatConf, agentInstance)
        SeeOther(Location(settingsUri))
      }
    case req @ GET -> Root / path =>
      static(path, req)
  }

  def static(file: String, request: Request[IO]): IO[Response[IO]] =
    StaticFile
      .fromResource("/" + file, Some(request))
      .getOrElseF(NotFound(Errors(s"Not found: '$file'.").asJson))

  implicit val deviceReadable: Readable[Device] = Readable.string.map(s => Device(s))
  implicit val tokenReadable: Readable[BoatToken] = Readable.string.map(s => BoatToken(s))

  val service = Router("/" -> routes).orNotFound

  def readForm(form: FormReader): Either[Errors, BoatConf] = for
    host <- form.readT[Host]("host")
    port <- form.readT[Port]("port")
    device <- form.readT[Device]("device")
    token <- form.readT[Option[BoatToken]]("token")
    enabled <- form.readT[Boolean]("enabled")
  yield BoatConf(host, port, device, token, enabled)

  def parseForm[T](req: Request[IO], read: FormReader => Either[Errors, T])(implicit
    decoder: EntityDecoder[IO, UrlForm]
  ) =
    decoder
      .decode(req, strict = false)
      .foldF(
        err => IO.raiseError(err),
        form =>
          read(new FormReader(form)).fold(err => IO.raiseError(err.asException), ok => IO.pure(ok))
      )

class FormReader(form: UrlForm):
  def read[T](key: String, build: Option[String] => Either[Errors, T]): Either[Errors, T] =
    build(form.getFirst(key))

  def readT[T](key: String)(implicit r: Readable[T]): Either[Errors, T] =
    read[T](key, s => r(s))
