package com.malliina.util

extension [L, R](e: Either[L, R])
  def recover[RR >: R](code: L => RR): RR = e.fold(code, identity)

  def recoverWith[RR >: R](code: L => Either[L, RR]): Either[L, RR] =
    e.fold(l => code(l), r => Right(r))
