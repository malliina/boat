package com.malliina.boat.ais

import akka.Done
import akka.stream.scaladsl.Source

import scala.concurrent.Future

object MqttSource {
  def apply(settings: MqttSettings): Source[MqMessage, Future[Done]] = {
    val graph = MqttGraph(settings)
    Source.fromGraph(graph)
  }
}
