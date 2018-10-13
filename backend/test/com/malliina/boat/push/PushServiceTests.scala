package com.malliina.boat.push

import com.malliina.boat.{BoatName, LocalConf}
import com.malliina.push.apns.APNSToken
import tests.BaseSuite

class PushServiceTests extends BaseSuite {
  ignore("push") {
    val conf = LocalConf.localConf
    val push = PushService(conf)
    val token = APNSToken("2fc8193335d7d41c3b4fbd9fc82a0590545db769bd3df1f6675ef505b3dee663")
    val result = await(push.push(BoatNotification(BoatName("TestBoat"), BoatState.Connected), token))
    assert(result.nonEmpty)
    //    println(result)
  }
}
