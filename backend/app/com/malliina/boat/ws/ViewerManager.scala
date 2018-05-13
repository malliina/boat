package com.malliina.boat.ws

import akka.actor.{Actor, ActorRef, Props, Terminated}
import com.malliina.boat.ws.ViewerManager.{BoatUpdate, NewViewer, log}
import com.malliina.play.models.Username
import play.api.Logger
import play.api.libs.json.JsValue

case class Viewer(out: ActorRef, user: Username)

object ViewerManager {
  private val log = Logger(getClass)

  def props() = Props(new ViewerManager)

  case class NewViewer(out: Viewer)

  case class BoatUpdate(message: JsValue, to: Username)

}

class ViewerManager extends Actor {
  var viewers: Set[Viewer] = Set.empty

  override def receive: Receive = {
    case NewViewer(viewer) =>
      context watch viewer.out
      viewers += viewer
      log.info(s"Viewer '${viewer.user}' joined.")
    case BoatUpdate(message, to) =>
      viewers.filter(_.user == to).foreach { viewer =>
        viewer.out ! message
      }
    case Terminated(out) =>
      viewers.find(v => v.out == out) foreach { viewer =>
        viewers -= viewer
        log.info(s"Viewer ${viewer.user} disconnected.")
      }
  }
}
