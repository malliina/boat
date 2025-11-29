package com.malliina.boat.cars

import cats.effect.Async
import cats.implicits.toTraverseOps
import cats.syntax.all.{catsSyntaxApplicativeError, toFlatMapOps, toFunctorOps}
import com.malliina.boat.cars.PolestarService.{log, toCar}
import com.malliina.boat.db.{RefreshService, TokenManager}
import com.malliina.boat.{Car, CarTelematics, CarsTelematics, VIN}
import com.malliina.http.{FullUrl, ResponseException}
import com.malliina.polestar.Polestar.Tokens
import com.malliina.polestar.{Polestar, PolestarCarInfo, Variables}
import com.malliina.util.AppLogger
import com.malliina.values.{AccessToken, ErrorMessage, RefreshToken, UserId}

object PolestarService:
  private val log = AppLogger(getClass)

  private def toCar(car: PolestarCarInfo, telematics: CarTelematics, image: FullUrl): Car =
    val content = car.content
    Car(
      car.vin,
      car.registrationNo,
      car.modelYear,
      car.software.version,
      content.interior.name,
      content.exterior.name,
      image,
      telematics
    )

class PolestarService[F[_]: Async](
  db: TokenManager[F],
  polestar: Polestar[F]
):
  private val F = Async[F]
  private val service = RefreshService.Polestar
  private var cache: Map[UserId, Tokens] = Map.empty

  def carsAndTelematics(owner: UserId): F[Seq[Car]] =
    request(owner, "cars"): token =>
      polestar
        .fetchCars(token)
        .flatMap: cs =>
          cs.traverse: car =>
            val vin = car.vin
            val image: Variables.Image =
              Variables.Image(car.pno34, car.structureWeek, car.modelYear)
            for
              images <- polestar.fetchCarImages(image, token)
              image <- effect(
                images.data.getCarImages.preferred
                  .map(_.url)
                  .toRight(ErrorMessage(s"No image for VIN: '$vin'.")),
                vin
              )
              t <- polestar.fetchTelematics(vin, token)
              ct <- effect(t.forVin(vin), vin)
            yield toCar(car, ct, image)

  private def effect[T](e: Either[ErrorMessage, T], vin: VIN): F[T] = e.fold(
    error => F.raiseError(CarException(error, vin, None)),
    t => F.pure(t)
  )

  def cars(owner: UserId): F[Seq[PolestarCarInfo]] =
    request(owner, "cars"): token =>
      polestar.fetchCars(token)

  def telematics(vin: VIN, owner: UserId): F[CarsTelematics] =
    request(owner, "telematics"): token =>
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
    yield saved

  private def storeNewTokens(updated: Tokens, owner: UserId): F[Tokens] =
    for
      _ <- db.removeTokens(owner, service)
      _ <- db.save(updated.refreshToken, service, owner)
    yield
      cache = cache.updated(owner, updated)
      updated

  private def request[T](owner: UserId, label: String)(task: AccessToken => F[T]): F[T] =
    tokensFor(owner).flatMap: tokens =>
      task(tokens.accessToken).handleErrorWith:
        case re: ResponseException if re.response.code == 401 =>
          log.info(
            s"Refreshing $service tokens for $owner because $label request to '${re.error.url}' returned 401...",
            re
          )
          refresh(tokens.refreshToken, owner)
            .adaptErr:
              case e: Exception => RefreshFailed(owner, e)
            .flatMap: updated =>
              task(updated.accessToken)
        case t =>
          F.raiseError(t)

  private def tokensFor(owner: UserId): F[Tokens] =
    cache
      .get(owner)
      .map(F.pure)
      .getOrElse:
        db.refreshTokens(owner, service)
          .flatMap: rts =>
            rts.headOption
              .map: rt =>
                log.info(
                  s"Refreshing $service token for user $owner because no in-memory token found..."
                )
                refresh(rt, owner).adaptErr:
                  case e: Exception => RefreshFailed(owner, e)
              .getOrElse:
                F.raiseError(NoTokens(owner))
