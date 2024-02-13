package com.malliina.boat.client.server

class WebServerTests extends munit.FunSuite:
  test("initial pass hash"):
    assert(WebServer.hash("boat") == WebServer.defaultHash)
