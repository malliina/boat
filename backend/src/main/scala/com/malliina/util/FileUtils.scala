package com.malliina.util

import java.nio.file.Paths

object FileUtils:
  val lineSep = sysPropsUnsafe("line.separator")
  private val userDirString = sysPropsUnsafe("user.dir")
  val userDir = Paths.get(userDirString)
  val userHome = Paths.get(sysPropsUnsafe("user.home"))
  val tempDir = Paths.get(sysPropsUnsafe("java.io.tmpdir"))

  def sysProps(key: String) = sys.props.get(key).toRight(s"System property not found: '$key'.")
  def sysPropsUnsafe(key: String) =
    sysProps(key).fold(err => throw java.util.NoSuchElementException(err), identity)
