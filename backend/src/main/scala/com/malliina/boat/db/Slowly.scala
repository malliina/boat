package com.malliina.boat.db

import cats.effect.Async
import cats.effect.std.Semaphore
import cats.effect.syntax.all.monadCancelOps_
import cats.implicits.{toFlatMapOps, toFunctorOps}

object Slowly:
  def default[F[_]: Async](n: Int) = Semaphore[F](n).map: semaphore =>
    Slowly(semaphore)

class Slowly[F[_]: Async](semaphore: Semaphore[F]):
  private val F = Async[F]

  def submit[T](task: F[T]): F[Option[T]] =
    submitOrElse(task.map(t => Option(t)), None)

  def submitOrElse[T](task: F[T], orElse: => T): F[T] = semaphore.tryAcquire.flatMap: acquired =>
    if acquired then task.guarantee(semaphore.release)
    else F.pure(orElse)
