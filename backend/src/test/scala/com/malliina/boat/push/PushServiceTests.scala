package com.malliina.boat.push

import com.malliina.boat.{BoatConf, BoatName}
import com.malliina.push.apns.APNSToken
import tests.{BaseSuite, MUnitSuite}

class PushServiceTests extends BaseSuite:
  http.test("push".ignore) { client =>
    val conf = BoatConf.parse().push.apns
    val push = APNSPush(conf, client)
    val token = APNSToken("2fc8193335d7d41c3b4fbd9fc82a0590545db769bd3df1f6675ef505b3dee663")
    val result =
      push.push(BoatNotification(BoatName("TestBoat"), BoatState.Connected), token).unsafeRunSync()
    assert(result.isEmpty)
  }
