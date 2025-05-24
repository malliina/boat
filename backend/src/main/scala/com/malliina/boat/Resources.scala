package com.malliina.boat

import com.malliina.storage.StorageLong
import com.malliina.util.AppLogger

import java.io.{Closeable, FileOutputStream, InputStream}
import java.nio.file.{Files, Path}

object Resources extends Resources

trait Resources:
  val log = AppLogger(getClass)

  def file(name: String, to: Path): Path =
    if Files.exists(to) && Files.size(to) > 0 then
      log.info(s"Found ${to.toAbsolutePath}, using it.")
      to
    else
      Files.deleteIfExists(to)
      Files.createDirectories(to.getParent)
      val resourcePath = s"com/malliina/boat/graph/$name"
      val resource = Option(getClass.getClassLoader.getResourceAsStream(resourcePath))
      resource
        .map: is =>
          using(is): res =>
            val target =
              if Files.isWritable(to) then to else Files.createTempFile("vaylat", ".json")
            write(res, target)
        .getOrElse:
          throw new Exception(s"Not found: '$resourcePath'.")

  def write(in: InputStream, to: Path) =
    using(new FileOutputStream(to.toFile, false)): out =>
      val size = in.transferTo(out).bytes
      log.info(s"Wrote '${to.toAbsolutePath}', $size.")
      to

  def using[T <: Closeable, U](t: T)(code: T => U) =
    try code(t)
    finally t.close()
