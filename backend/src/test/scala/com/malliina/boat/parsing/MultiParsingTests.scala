package com.malliina.boat.parsing

import com.malliina.boat.{DeviceId, DeviceName, TrackId, TrackMetaShort, TrackName}
import com.malliina.values.Username
import org.typelevel.ci.CIStringSyntax

object MultiParsingTests:
  def testFrom = TrackMetaShort(
    TrackId.unsafe(1),
    TrackName.unsafe("test"),
    DeviceId.unsafe(1),
    DeviceName.unsafe(ci"boat"),
    Username.unsafe("u")
  )
