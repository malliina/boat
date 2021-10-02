package com.malliina.boat.client.server

import cats.effect.unsafe.IORuntime
import cats.effect.kernel.Temporal
import cats.effect.IO
import com.malliina.boat.client.DeviceAgent
import com.malliina.boat.client.server.Device.GpsDevice
import com.malliina.http.FullUrl
import okhttp3.OkHttpClient

object AgentInstance:
  def apply(
    initial: Option[BoatConf],
    url: FullUrl,
    http: OkHttpClient,
//    t: Temporal[IO],
    rt: IORuntime
  ): AgentInstance =
    new AgentInstance(initial, url, http)(rt)

class AgentInstance(initialConf: Option[BoatConf], url: FullUrl, http: OkHttpClient)(implicit
  //  t: Temporal[IO],
  rt: IORuntime
):
  private var conf: Option[BoatConf] = initialConf
  private var resOpt = conf.map { c =>
    val res = DeviceAgent(c, url, http).allocated.unsafeRunSync()
    if c.enabled then res._1.connect().unsafeRunAndForget()
    res
  }
//  if initialConf.exists(_.enabled) then agent.connect().unsafeRunAndForget()

  def updateIfNecessary(newConf: BoatConf): Boolean = synchronized {
    if !conf.contains(newConf) then
      val newUrl =
        if newConf.device == GpsDevice then DeviceAgent.DeviceUrl else DeviceAgent.BoatUrl
      conf = Option(newConf)
      resOpt.foreach(_._2.unsafeRunSync())
//      val oldAgent = agent
//      finalizer.unsafeRunSync()
//      oldAgent.close()
      val newRes = DeviceAgent(newConf, newUrl, http).allocated.unsafeRunSync()
      resOpt = Option(newRes)
      if newConf.enabled then newRes._1.connect().unsafeRunAndForget()
      true
    else false
  }
