package com.malliina.boat.ws

import akka.actor.{ActorRef, Props}
import com.malliina.boat.ws.ViewerActor.ViewerClient
import com.malliina.boat.ws.ViewerManager.NewViewer
import com.malliina.play.models.Username
import com.malliina.play.ws.{ActorConfig, JsonActor}
import play.api.mvc.RequestHeader


object ViewerActor {
  def props(ctx: ViewerClient) = Props(new ViewerActor(ctx))

  case class ViewerClient(user: Username, mediator: ActorRef, out: ActorRef, rh: RequestHeader)
    extends ActorConfig[Username]

}

class ViewerActor(ctx: ViewerClient) extends JsonActor(ctx) {
  override def preStart(): Unit = {
    super.preStart()
    ctx.mediator ! NewViewer(Viewer(out, ctx.user))
  }
}
