package com.malliina.values

import scala.quoted.{Expr, Quotes, quotes}

opaque type Degrees = Float
object Degrees extends ValidatingCompanion[Float, Degrees]:
  val min = 0f
  val max = 360f
  def unsafe(in: Float): Degrees = in
  override def build(input: Float): Either[ErrorMessage, Degrees] =
    if input >= min && input <= max then Right(input) else Left(defaultError(input))
  override def write(t: Degrees): Float = t
  override def defaultError(in: Float): ErrorMessage = ErrorMessage(
    s"Invalid degrees: '$in'. Must be [$min, $max]."
  )
extension (d: Degrees) def float: Float = d
extension (i: Float) inline def degrees: Degrees = Literals.deg(i)

object Literals:
  inline def deg(inline x: Float) = ${ parseDeg('x) }

  def parseDeg(x: Expr[Float])(using Quotes): Expr[Degrees] =
    val f = x.valueOrAbort
    Degrees
      .build(f)
      .fold(
        err =>
          quotes.reflect.report.error(err.message)
          ???
        ,
        ok => '{ Degrees.unsafe(${ Expr(f) }) }
      )
