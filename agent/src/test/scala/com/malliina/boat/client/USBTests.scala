package com.malliina.boat.client

import gnu.io.CommPortIdentifier
import org.scalatest.FunSuite

import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter

class USBTests extends FunSuite {
  ignore("usb") {
    val serialPorts = CommPortIdentifier.getPortIdentifiers.asScala.toList
      .map(_.asInstanceOf[CommPortIdentifier])
      .filter(_.getPortType == CommPortIdentifier.PORT_SERIAL)
    serialPorts.foreach(id => println(id.getName))
  }
}
