package com.malliina.boat.http4s

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.{IO, Sync}
import cats.implicits._
import com.malliina.boat.Errors
import com.malliina.boat.db.MissingCredentialsException
import com.malliina.boat.http4s.BasicService.{log, noCache}
import com.malliina.util.AppLogger
import org.http4s.CacheDirective._
import org.http4s._
import org.http4s.headers.{Accept, Location, `Cache-Control`}

import scala.concurrent.duration.FiniteDuration

object BasicService extends BasicService[IO] {
  private val log = AppLogger(getClass)

  val noCache = `Cache-Control`(`no-cache`(), `no-store`, `must-revalidate`)
  def cached(duration: FiniteDuration) = `Cache-Control`(
    NonEmptyList.of(`max-age`(duration), `public`)
  )

  def ranges(headers: Headers) = headers
    .get(Accept)
    .map(_.values.map(_.mediaRange))
    .getOrElse(NonEmptyList.of(MediaRange.`*/*`))
}

class BasicService[F[_]: Applicative: Sync] extends Implicits[F] {
  def temporaryRedirect(uri: Uri) = TemporaryRedirect(Location(uri), noCache)
  def seeOther(uri: Uri) = SeeOther(Location(uri), noCache)
  def ok[A](a: A)(implicit w: EntityEncoder[F, A]) = Ok(a, noCache)
  def badRequest[A](a: A)(implicit w: EntityEncoder[F, A]) = BadRequest(a, noCache)
  def notFoundReq(req: Request[F]): F[Response[F]] =
    notFound(Errors(s"Not found: '${req.uri}'."))
  def notFound[A](a: A)(implicit w: EntityEncoder[F, A]): F[Response[F]] =
    NotFound(a, noCache)
  def serverError[A](a: A)(implicit w: EntityEncoder[F, A]): F[Response[F]] =
    InternalServerError(a, noCache)

  def errorHandler(t: Throwable): F[Response[F]] = t match {
    case ir: InvalidRequest =>
      Sync[F].delay(log.warn(ir.message, ir)).flatMap { _ =>
        badRequest(ir.errors)
      }
    case mce: MissingCredentialsException =>
      notFound(Errors(mce.error.message))
    case other =>
      Sync[F].delay(log.error("Server error.", other)).flatMap { _ =>
        serverError(Errors("Server error."))
      }
  }
}
