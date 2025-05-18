package com.malliina.boat.cars

import cats.effect.Async
import cats.syntax.all.{catsSyntaxApplicativeError, toFlatMapOps, toFunctorOps}
import com.malliina.boat.VIN
import com.malliina.boat.db.{RefreshService, TokenManager}
import com.malliina.http.ResponseException
import com.malliina.polestar.Polestar.Tokens
import com.malliina.polestar.{CarTelematics, Polestar, PolestarCarInfo}
import com.malliina.values.{AccessToken, RefreshToken, UserId}
import com.malliina.util.AppLogger
import com.malliina.boat.cars.PolestarService.log

object PolestarService:
  private val log = AppLogger(getClass)

class PolestarService[F[_]: Async](
  db: TokenManager[F],
  polestar: Polestar[F]
):
  private val F = Async[F]
  val service = RefreshService.Polestar
  private var cache: Map[UserId, Tokens] = Map.empty

  def cars(owner: UserId): F[Seq[PolestarCarInfo]] =
    request(owner): token =>
      polestar.fetchCars(token)

  def telematics(vin: VIN, owner: UserId): F[CarTelematics] =
    request(owner): token =>
      polestar.fetchTelematics(vin, token)

  def save(creds: Polestar.Creds, owner: UserId): F[Tokens] =
    for
      tokens <- polestar.auth.fetchTokens(creds)
      saved <- storeNewTokens(tokens, owner)
    yield saved

  private def refresh(refreshToken: RefreshToken, owner: UserId): F[Tokens] =
    for
      ts <- polestar.auth.refresh(refreshToken)
      saved <- storeNewTokens(ts, owner)
    yield
      log.info(s"Refreshed Polestar tokens for '$owner")
      saved

  private def storeNewTokens(updated: Tokens, owner: UserId): F[Tokens] =
    for
      removedCount <- db.removeTokens(owner, service)
      saved <- db.save(updated.refreshToken, service, owner)
    yield
      cache = cache.updated(owner, updated)
      updated

  private def request[T](owner: UserId)(task: AccessToken => F[T]): F[T] =
    tokensFor(owner).flatMap: tokens =>
      task(tokens.accessToken).handleErrorWith:
        case re: ResponseException if re.response.code == 401 =>
          refresh(tokens.refreshToken, owner).flatMap: updated =>
            task(updated.accessToken)
        case t =>
          F.raiseError(t)

  private def tokensFor(owner: UserId): F[Tokens] =
    cache
      .get(owner)
      .map(F.pure)
      .getOrElse:
        db.refreshTokens(owner, RefreshService.Polestar)
          .flatMap: rts =>
            rts.headOption
              .map: rt =>
                refresh(rt, owner).adaptErr:
                  case e: Exception => RefreshFailed(owner, e)
              .getOrElse:
                F.raiseError(NoTokens(owner))
