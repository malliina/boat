package com.malliina

import com.malliina.concurrent.Execution.cached

import scala.concurrent.Future

package object boat {

  implicit class EitherOps[L, R](e: Either[L, R]) {
    def recover[L2 >: R](onLeft: L => L2): L2 = e.fold(onLeft, identity)

    def asOption(onLeft: L => Unit): Option[R] = e.map(Option.apply).recover { l => onLeft(l); None }
  }

  implicit class FutureEitherOps[L, R](fe: Future[Either[L, R]]) {
    def mapRight[LL >: L, S](code: R => Either[LL, S]): Future[Either[LL, S]] =
      fe.map(e => e.flatMap(r => code(r)))

    def mapR[S](code: R => S): Future[Either[L, S]] =
      fe.map(e => e.map(r => code(r)))

    def flatMapRight[LL >: L, S](code: R => Future[Either[LL, S]]): Future[Either[LL, S]] =
      fe.flatMap(e => e.fold(l => Future.successful(Left(l)), r => code(r)))

    def flatMapR[S](code: R => Future[S]): Future[Either[L, S]] =
      fe.flatMapRight(r => code(r).map(s => Right(s)))

    def onFail[S >: R](code: L => S): Future[S] =
      fe.map(e => e.fold(l => code(l), identity))
  }

}
