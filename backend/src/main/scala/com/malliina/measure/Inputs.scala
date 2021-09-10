package com.malliina.measure

import com.malliina.boat.SingleError

import scala.util.Try

object Inputs:
  def toDouble(s: String) =
    Try(s.toDouble).toOption.toRight(SingleError.input(s"Not a double: '$s'."))

  def toInt(s: String) =
    Try(s.toInt).toOption.toRight(SingleError.input(s"Not an integer: '$s'."))
