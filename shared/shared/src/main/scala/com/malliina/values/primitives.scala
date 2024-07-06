package com.malliina.values

import com.malliina.boat.{Coord, Latitude, Longitude}
import com.malliina.values.Literals.ErrorMessageLiteral

import scala.quoted.{Expr, Quotes, quotes}

given Readable[Float] = Readable.double.map(_.toFloat)

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
extension (i: Float) inline def degrees: Degrees = ${ Literals.DegreesLiteral('i) }
extension (i: Double)
  inline def lat: Latitude = ${ Literals.LatitudeLiteral('i) }
  inline def lng: Longitude = ${ Literals.LongitudeLiteral('i) }
  inline infix def lngLat(lat: Double): Coord = Coord(i.lng, lat.lat)

extension (s: String)
  inline def err: ErrorMessage = ${ Literals.ErrorMessageLiteral('s) }
  def error = ErrorMessage(s)
extension (inline ctx: StringContext)
  inline def err(inline args: Any*): ErrorMessage =
    ${ Literals.ErrorMessageLiteralCtx('ctx, 'args) }

extension [T](e: Either[ErrorMessage, T])
  def getUnsafe: T = e.fold(err => throw IllegalArgumentException(err.message), identity)

object Literals:
  object DegreesLiteral extends LiteralFloat[Degrees]:
    override def parse(in: Float)(using Quotes): Either[ErrorMessage, Expr[Degrees]] =
      Degrees
        .build(in)
        .map: _ =>
          '{ Degrees.unsafe(${ Expr(in) }) }

  object LatitudeLiteral extends LiteralDouble[Latitude]:
    override def parse(in: Double)(using Quotes): Either[ErrorMessage, Expr[Latitude]] =
      Latitude
        .build(in)
        .map: _ =>
          '{ Latitude.build(${ Expr(in) }).getUnsafe }

  object LongitudeLiteral extends LiteralDouble[Longitude]:
    override def parse(in: Double)(using Quotes): Either[ErrorMessage, Expr[Longitude]] =
      Longitude
        .build(in)
        .map: _ =>
          '{ Longitude.build(${ Expr(in) }).getUnsafe }

  object ErrorMessageLiteral extends LiteralString[ErrorMessage]:
    override def parse(in: String)(using Quotes): Either[ErrorMessage, Expr[ErrorMessage]] =
      if in.nonEmpty then Right('{ ErrorMessage(${ Expr(in) }) })
      else Left(ErrorMessage("Error message must be non-empty."))

  object ErrorMessageLiteralCtx extends LiteralStringContext[ErrorMessage]:
    override def parse(in: String)(using Quotes): Either[ErrorMessage, Expr[ErrorMessage]] =
      if in.nonEmpty then Right('{ ErrorMessage(${ Expr(in) }) })
      else Left(ErrorMessage("Error message must be non-empty."))

trait LiteralInt[T]:
  def parse(in: Int)(using Quotes): Either[ErrorMessage, Expr[T]]

  def apply(x: Expr[Int])(using Quotes): Expr[T] =
    val f = x.valueOrAbort
    parse(f)
      .fold(
        err =>
          quotes.reflect.report.error(err.message)
          ???
        ,
        ok => ok
      )

trait LiteralDouble[T]:
  def parse(in: Double)(using Quotes): Either[ErrorMessage, Expr[T]]

  def apply(x: Expr[Double])(using Quotes): Expr[T] =
    val f = x.valueOrAbort
    parse(f)
      .fold(
        err =>
          quotes.reflect.report.error(err.message)
          ???
        ,
        ok => ok
      )

trait LiteralFloat[T]:
  def parse(in: Float)(using Quotes): Either[ErrorMessage, Expr[T]]

  def apply(x: Expr[Float])(using Quotes): Expr[T] =
    val f = x.valueOrAbort
    parse(f)
      .fold(
        err =>
          quotes.reflect.report.error(err.message)
          ???
        ,
        ok => ok
      )

trait LiteralString[T]:
  def parse(in: String)(using Quotes): Either[ErrorMessage, Expr[T]]

  def apply(x: Expr[String])(using Quotes): Expr[T] =
    val f = x.valueOrAbort
    parse(f)
      .fold(
        err =>
          quotes.reflect.report.error(err.message)
          ???
        ,
        ok => ok
      )

trait LiteralStringContext[T]:
  def parse(in: String)(using Quotes): Either[ErrorMessage, Expr[T]]

  def apply(x: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes): Expr[T] =
    val parts = x.valueOrAbort.parts
    if parts.size == 1 then
      parse(parts.head).fold(
        err =>
          quotes.reflect.report.error(err.message)
          ???
        ,
        ok => ok
      )
    else
      quotes.reflect.report.error("interpolation not supported", argsExpr)
      ???
