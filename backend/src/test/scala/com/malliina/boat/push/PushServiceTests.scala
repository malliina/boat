package com.malliina.boat.push

import cats.effect.IO
import com.malliina.boat.{AppConf, BaseSuite, BoatConf, BoatName}
import com.malliina.push.apns.APNSToken
import org.typelevel.ci.CIStringSyntax

class PushServiceTests extends BaseSuite:
  http.test("push".ignore): client =>
    val conf = parseUnsafe().push.apns
    val push = APNSPush.fromConf(conf, client)
    val token = APNSToken("e42535429cb5b042f4d7fbec43d90a21a9e22a33f47d939fed6f82eb37da3670")
    val task: IO[PushSummary] =
      push.push(
        SourceNotification(AppConf.Name, BoatName(ci"TestBoat"), SourceState.Connected, None),
        token
      )
    task.map: result =>
      assert(result.noBadTokensOrReplacements)
      assert(result.iosSuccesses.contains(token))

  def parseUnsafe() =
    BoatConf.parseBoat().fold(err => throw err, identity)
