package com.malliina.boat.parsing

import com.malliina.boat.{BaseSuite, DeviceName, DeviceId, RawSentence, TrackId, TrackMetaShort, TrackName}
import com.malliina.util.FileUtils
import com.malliina.values.Username
import org.typelevel.ci.CIStringSyntax

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.jdk.CollectionConverters.CollectionHasAsScala

object MultiParsingTests:
  def testFrom = TrackMetaShort(
    TrackId.unsafe(1),
    TrackName.unsafe("test"),
    DeviceId.unsafe(1),
    DeviceName.unsafe(ci"boat"),
    Username.unsafe("u")
  )
