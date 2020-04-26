package com.malliina.boat.client

import gnu.io.CommPortIdentifier

import scala.jdk.CollectionConverters.EnumerationHasAsScala

class USBTests extends munit.FunSuite {
  test("usb".ignore) {
    val serialPorts = CommPortIdentifier.getPortIdentifiers.asScala.toList
      .map(_.asInstanceOf[CommPortIdentifier])
      .filter(_.getPortType == CommPortIdentifier.PORT_SERIAL)
    serialPorts.foreach(id => println(id.getName))
  }
}
