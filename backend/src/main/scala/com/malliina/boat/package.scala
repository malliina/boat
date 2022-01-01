package com.malliina

import cats.effect.IO
import com.malliina.web.JWTError

import scala.concurrent.Future

package object boat:

  implicit class EitherOps[L, R](e: Either[L, R]):
    def recover[L2 >: R](onLeft: L => L2): L2 = e.fold(onLeft, identity)

    def asOption(onLeft: L => Unit): Option[R] = e.map(Option.apply).recover { l =>
      onLeft(l); None
    }
