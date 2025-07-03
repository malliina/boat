package com.malliina.boat.geo

import cats.effect.Sync
import cats.syntax.all.{catsSyntaxApplicativeError, catsSyntaxFlatMapOps, toFunctorOps}
import com.malliina.boat.Coord
import com.malliina.util.AppLogger

import java.util.Base64

case class Size(width: Int, height: Int):
  def wxh = s"${width}x$height"
  override def toString = wxh

object ImageApi:
  private val log = AppLogger(getClass)

  def noop[F[_]: Sync] = new ImageApi[F]:
    override def image(coord: Coord, size: Size): F[Array[Byte]] =
      Sync[F].raiseError(Exception("No images."))

trait ImageApi[F[_]: Sync]:
  private val F = Sync[F]

  def image(coord: Coord, size: Size): F[Array[Byte]]

  def imageEncoded(coord: Coord, size: Size = Size(55, 55)): F[Option[String]] =
    image(coord, size)
      .map: bytes =>
        Option(Base64.getEncoder.encodeToString(bytes))
      .handleErrorWith: e =>
        F.delay(ImageApi.log.error(s"Image lookup of $coord failed.", e)) >> F.pure(None)
