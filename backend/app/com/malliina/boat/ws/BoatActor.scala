package com.malliina.boat.ws

import akka.actor.{ActorRef, Props}
import com.malliina.boat.db.TrackMeta
import com.malliina.boat.ws.BoatActor.BoatClient
import com.malliina.boat.ws.BoatManager.{BoatMessage, NewBoat}
import com.malliina.boat.{BoatName, TrackName}
import com.malliina.play.models.Username
import com.malliina.play.ws.{ClientContext, JsonActor}
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader

object BoatActor {
  def props(ctx: BoatClient) = Props(new BoatActor(ctx))

  case class BoatClient(user: Username,
                        boat: BoatName,
                        track: TrackName,
                        mediator: ActorRef,
                        out: ActorRef,
                        rh: RequestHeader) extends ClientContext with TrackMeta

}

class BoatActor(ctx: BoatClient) extends JsonActor(ctx) {
  val boat = Boat(out, ctx.user, ctx.boat, ctx.track)

  override def preStart(): Unit = {
    super.preStart()
    ctx.mediator ! NewBoat(boat)
  }

  override def onMessage(message: JsValue): Unit = {
    ctx.mediator ! BoatMessage(message, boat)
  }
}
