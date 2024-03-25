package com.malliina.boat

extension (e: Exception) def message = Option(e.getMessage)

extension [L, R](e: Either[L, R])
  def recover[L2 >: R](onLeft: L => L2): L2 = e.fold(onLeft, identity)

  def asOption(onLeft: L => Unit): Option[R] = e
    .map(Option.apply)
    .recover: l =>
      onLeft(l)
      None
