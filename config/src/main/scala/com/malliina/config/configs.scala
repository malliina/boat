package com.malliina.config

import com.malliina.http.FullUrl
import com.malliina.values.{ErrorMessage, Readable}
import com.typesafe.config.Config

import java.nio.file.{InvalidPathException, Path, Paths}

implicit class ConfigOps(c: Config) extends AnyVal:
  def read[T](key: String)(implicit r: ConfigReadable[T]): Either[ErrorMessage, T] =
    r.read(key, c)
  def unsafe[T: ConfigReadable](key: String): T =
    c.read[T](key).fold(err => throw new IllegalArgumentException(err.message), identity)

trait ConfigReadable[T]:
  def read(key: String, c: Config): Either[ErrorMessage, T]
  def flatMap[U](f: T => ConfigReadable[U]): ConfigReadable[U] =
    val parent = this
    (key: String, c: Config) => parent.read(key, c).flatMap(t => f(t).read(key, c))
  def emap[U](f: T => Either[ErrorMessage, U]): ConfigReadable[U] = (key: String, c: Config) =>
    read(key, c).flatMap(f)
  def map[U](f: T => U): ConfigReadable[U] = emap(t => Right(f(t)))

object ConfigReadable:
  implicit val string: ConfigReadable[String] = (key: String, c: Config) => Right(c.getString(key))
  implicit val url: ConfigReadable[FullUrl] = string.emap(s => FullUrl.build(s))
  implicit val int: ConfigReadable[Int] = (key: String, c: Config) => Right(c.getInt(key))
  implicit val bool: ConfigReadable[Boolean] = (key: String, c: Config) => Right(c.getBoolean(key))
  implicit val config: ConfigReadable[Config] = (key: String, c: Config) => Right(c.getConfig(key))
  implicit val path: ConfigReadable[Path] = ConfigReadable.string.emap { s =>
    try Right(Paths.get(s))
    catch case ipe: InvalidPathException => Left(ErrorMessage(s"Invalid path: '$s'."))
  }
  implicit def readable[T](implicit r: Readable[T]): ConfigReadable[T] =
    string.emap(s => r.read(s))
