package com.malliina.util

import java.nio.file.Paths

object FileUtils {
  val lineSep = sys.props("line.separator")
  val userDirString = sys.props("user.dir")
  val userDir = Paths.get(userDirString)
  val userHome = Paths.get(sys.props("user.home"))
  val tempDir = Paths.get(sys.props("java.io.tmpdir"))
}
