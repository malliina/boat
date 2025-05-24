package com.malliina.http

import cats.effect.Async
import cats.effect.std.Dispatcher
import cats.syntax.all.toFlatMapOps
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder}
import org.scalajs.dom
import org.scalajs.dom.{AbortController, Headers, HttpMethod, RequestCredentials, RequestInit, Response}

import scala.scalajs.js

extension [A](t: js.Thenable[A])
  def effect[F[_]: Async]: F[A] =
    cancelable(Async[F].unit)

  def cancelable[F[_]: Async](abort: AbortController): F[A] =
    val onCancel = Async[F].delay:
      abort.abort()
    cancelable(onCancel)

  def cancelable[F[_]: Async](onCancel: F[Unit]): F[A] =
    val F = Async[F]
    F.async[A]: cb =>
      t.`then`(
        a => cb(Right(a)),
        err => cb(Left(js.special.wrapAsThrowable(err)))
      )
      F.delay[Option[F[Unit]]](Some(onCancel))

class Http[F[_]](val client: HttpClient[F], val dispatcher: Dispatcher[F]):
  def run[R](task: F[R]): Unit = dispatcher.unsafeRunAndForget(task)
  def using[R](request: HttpClient[F] => F[R]): Unit = run(request(client))

class HttpClient[F[_]: Async](csrf: CSRFConf):
  private val F = Async[F]

  def get[R: Decoder](uri: String): F[R] =
    fetch(HttpMethod.GET, uri).flatMap: xhr =>
      validate[R](uri, xhr)

  def post[W: Encoder, R: Decoder](uri: String, data: W): F[R] =
    makeAjax(HttpMethod.POST, uri, data)

  def patch[W: Encoder, R: Decoder](uri: String, data: W): F[R] =
    makeAjax(HttpMethod.PATCH, uri, data)

  def put[W: Encoder, R: Decoder](uri: String, data: W): F[R] =
    makeAjax(HttpMethod.PUT, uri, data)

  private def makeAjax[W: Encoder, R: Decoder](
    method: HttpMethod,
    uri: String,
    data: W
  ): F[R] =
    val headers = Map(
      "Content-Type" -> "application/json",
      csrf.headerName.toString -> csrf.noCheck
    )
    fetch(method, uri, data.asJson.noSpaces, headers = headers).flatMap: res =>
      validate[R](uri, res)

  private def validate[R: Decoder](uri: String, res: dom.Response): F[R] =
    val status = res.status
    if status >= 200 && status <= 300 then
      res
        .text()
        .effect
        .flatMap: str =>
          decode[R](str).fold(
            err => F.raiseError(JsonException(err, res)),
            ok => F.pure(ok)
          )
    else F.raiseError(StatusException(uri, res))

  def fetch(
    method: HttpMethod,
    url: String,
    data: String | Unit = (),
    headers: Map[String, String] = Map.empty,
    credentials: RequestCredentials = RequestCredentials.include
  ): F[Response] =
    val promise = F.delay:
      val aborter = AbortController()
      val req = new RequestInit {}
      req.method = method
      req.body = data
      req.credentials = credentials
      val hs = new Headers()
      headers.foreach((name, value) => hs.append(name, value))
      req.headers = hs
      req.signal = aborter.signal
      dom.fetch(url, req).cancelable(aborter)
    promise.flatten

class JsonException(val error: io.circe.Error, val res: dom.Response) extends Exception

class StatusException(val uri: String, val res: dom.Response)
  extends Exception(s"Invalid response code '${res.status}' from '$uri'.")
