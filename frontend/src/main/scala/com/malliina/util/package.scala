package com.malliina

package object util:

  implicit class EitherOps[L, R](val e: Either[L, R]) extends AnyVal:
    def recover[RR >: R](code: L => RR): RR = e.fold(code, identity)

    def recoverWith[RR >: R](code: L => Either[L, RR]): Either[L, RR] =
      e.fold(l => code(l), r => Right(r))
