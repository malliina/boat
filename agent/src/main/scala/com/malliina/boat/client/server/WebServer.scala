package com.malliina.boat.client.server

import cats.effect.Async
import cats.syntax.all.toFlatMapOps
import com.malliina.boat.client.TcpClient
import com.malliina.boat.client.{FormReadable, FormReader}
import com.malliina.boat.client.TcpClient.{host, port}
import com.malliina.boat.client.server.AgentHtml.{asHtml, boatForm}
import com.malliina.boat.client.server.AgentSettings.{readConf, saveAndReload}
import com.malliina.boat.client.server.WebServer.{noCache, settingsUri}
import com.malliina.boat.{BoatToken, Errors}
import com.malliina.util.AppLogger
import com.malliina.values.Readable
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
import org.http4s.headers.`Cache-Control`
import org.slf4j.Logger

import java.nio.charset.StandardCharsets

trait AppImplicits[F[_]]
  extends syntax.AllSyntax
  with Http4sDsl[F]
  with CirceInstances
  with ScalatagsEncoder

object WebServer:
  val log: Logger = AppLogger(getClass)
  val boatCharset = StandardCharsets.UTF_8
  // MD5 hash of the default password "boat"
  val defaultHash = "dd8fc45d87f91c6f9a9f43a3f355a94a"

  val changePassRoute = "init"
  val changePassUri = uri"/init"
  val settingsPath = "settings"
  val settingsUri = uri"/settings"
  val noCache = `Cache-Control`(`no-cache`(), `no-store`, `must-revalidate`)

  def hash(pass: String): String = DigestUtils.md5Hex(pass)

class WebServer[F[_]: Async](agentInstance: AgentInstance[F]) extends AppImplicits[F]:
  val boatUser = "boat"
  val tempUser = "temp"

  private val routes = HttpRoutes.of[F]:
    case GET -> Root =>
      SeeOther(Location(settingsUri))
    case GET -> Root / "settings" =>
      Ok(asHtml(boatForm(readConf().toOption)), noCache)
    case req @ POST -> Root / "settings" =>
      parseForm(req, readForm).flatMap: boatConf =>
        saveAndReload(boatConf, agentInstance)
        SeeOther(Location(settingsUri))
    case req @ GET -> Root / path =>
      static(path, req)

  private def static(file: String, request: Request[F]): F[Response[F]] =
    StaticFile
      .fromResource("/" + file, Some(request))
      .getOrElseF(NotFound(Errors(s"Not found: '$file'.").asJson))

  given Readable[Device] = Readable.string.map(s => Device(s))
  given Readable[BoatToken] = Readable.string.map(s => BoatToken(s))

  val service = Router("/" -> routes).orNotFound
  val F = Async[F]

  private def readForm(form: FormReader): Either[Errors, BoatConf] = for
    host <- form.read[Host]("host")
    port <- form.read[Port]("port")
    device <- form.read[Device]("device")
    token <- form.read[Option[BoatToken]]("token")
    enabled <- form.read[Boolean]("enabled")
  yield BoatConf(host, port, device, token, enabled)

  private def parseForm[T](req: Request[F], read: FormReader => Either[Errors, T])(using
    decoder: EntityDecoder[F, UrlForm]
  ): F[T] =
    decoder
      .decode(req, strict = false)
      .foldF(
        err => F.raiseError(err),
        form => read(FormReader(form)).fold(err => F.raiseError(err.asException), ok => F.pure(ok))
      )
