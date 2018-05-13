package com.malliina.boat.ws

import akka.actor.{ActorRef, Props}
import com.malliina.boat.ws.BoatActor.BoatClient
import com.malliina.boat.ws.BoatManager.{BoatMessage, NewBoat}
import com.malliina.boat.{Track, Utils}
import com.malliina.play.models.Username
import com.malliina.play.ws.{ClientContext, JsonActor}
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader

object BoatActor {
  def props(ctx: BoatClient) = Props(new BoatActor(ctx))

  case class BoatClient(user: Username, mediator: ActorRef, out: ActorRef, rh: RequestHeader)
    extends ClientContext

}

class BoatActor(ctx: BoatClient) extends JsonActor(ctx) {
  def randomTrackId = Utils.randomString(6)

  override def preStart(): Unit = {
    super.preStart()
    ctx.mediator ! NewBoat(Boat(out, ctx.user, Track.randomName()))
  }

  override def onMessage(message: JsValue): Unit = {
    ctx.mediator ! BoatMessage(message, ctx.user)
  }
}
