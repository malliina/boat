package com.malliina.boat.client.server

import cats.effect.{Blocker, ContextShift, IO}
import com.malliina.boat.client.Logging
import com.malliina.boat.client.server.AgentHtml.{asHtml, boatForm}
import com.malliina.boat.client.server.AgentSettings.{readConf, saveAndReload}
import com.malliina.boat.client.server.WebServer.settingsUri
import com.malliina.boat.{BoatToken, Errors, Readable}
import io.circe.syntax.EncoderOps
import org.apache.commons.codec.digest.DigestUtils
import org.http4s.circe.CirceInstances
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.server.Router
import org.http4s._

import java.nio.charset.StandardCharsets

trait AppImplicits
  extends syntax.AllSyntaxBinCompat
  with Http4sDsl[IO]
  with CirceInstances
  with ScalatagsEncoder

object WebServer {
  val log = Logging(getClass)
  val boatCharset = StandardCharsets.UTF_8
  // MD5 hash of the default password "boat"
  val defaultHash = "dd8fc45d87f91c6f9a9f43a3f355a94a"

  val changePassRoute = "init"
  val changePassUri = uri"/$changePassRoute"
  val settingsPath = "settings"
  val settingsUri = uri"/$settingsPath"

  def apply(agentInstance: AgentInstance, blocker: Blocker, cs: ContextShift[IO]): WebServer =
    new WebServer(agentInstance, blocker)(cs)

  def hash(pass: String): String = DigestUtils.md5Hex(pass)
}

class WebServer(agentInstance: AgentInstance, blocker: Blocker)(implicit cs: ContextShift[IO])
  extends AppImplicits {

  val boatUser = "boat"
  val tempUser = "temp"

  val routes = HttpRoutes.of[IO] {
    case GET -> Root =>
      SeeOther(Location(settingsUri))
    case GET -> Root / "settings" =>
      Ok(asHtml(boatForm(readConf())))
    case req @ GET -> Root / "settings" =>
      parseForm(req, readForm).flatMap { boatConf =>
        saveAndReload(boatConf, agentInstance)
        SeeOther(Location(settingsUri))
      }
    case req @ GET -> Root / path =>
      static(path, req)
  }

  def static(file: String, request: Request[IO]) =
    StaticFile
      .fromResource("/" + file, blocker, Some(request))
      .getOrElseF(NotFound(Errors(s"Not found: '$file'.").asJson))

  implicit val deviceReadable: Readable[Device] = Readable.string.map(s => Device(s))
  implicit val tokenReadable: Readable[BoatToken] = Readable.string.map(s => BoatToken(s))

  val service = Router("/" -> routes).orNotFound

  def readForm(form: FormReader): Either[Errors, BoatConf] = for {
    host <- form.readT[String]("host")
    port <- form.readT[Int]("port")
    device <- form.readT[Device]("device")
    token <- form.readT[Option[BoatToken]]("token")
    enabled <- form.readT[Boolean]("enabled")
  } yield BoatConf(host, port, device, token, enabled)

  def parseForm[T](req: Request[IO], read: FormReader => Either[Errors, T])(implicit
    decoder: EntityDecoder[IO, UrlForm]
  ) = {
    decoder
      .decode(req, strict = false)
      .foldF(
        err => IO.raiseError(err),
        form =>
          read(new FormReader(form)).fold(err => IO.raiseError(err.asException), ok => IO.pure(ok))
      )
  }
}

class FormReader(form: UrlForm) {
  def read[T](key: String, build: Option[String] => Either[Errors, T]): Either[Errors, T] =
    build(form.getFirst(key))

  def readT[T](key: String)(implicit r: Readable[T]): Either[Errors, T] =
    read[T](key, s => r(s))
}
