package com.malliina.boat.client.server

import com.malliina.boat.client.AsyncSuite

class WebServerTests extends AsyncSuite {
  test("initial pass hash") {
    assert(WebServer.hash("boat") == WebServer.defaultHash)
  }
}
