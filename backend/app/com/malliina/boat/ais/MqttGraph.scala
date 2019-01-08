package com.malliina.boat.ais

import java.util.concurrent.Semaphore

import akka.Done
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}
import akka.util.ByteString
import org.eclipse.paho.client.mqttv3._
import play.api.Logger

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import MqttGraph.log
object MqttGraph {
  private val log = Logger(getClass)

  def apply(settings: MqttSettings): MqttGraph =
    new MqttGraph(settings)
}

// https://github.com/akka/alpakka/blob/ae2e1e1d44d627ebc66a24ea35993398df7840bc/mqtt/src/main/scala/MqttSource.scala
class MqttGraph(settings: MqttSettings)
  extends GraphStageWithMaterializedValue[SourceShape[MqMessage], Future[Done]] {

  val out = Outlet[MqMessage]("MqMessage.out")

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Done]) = {
    val subscriptionPromise = Promise[Done]()
    val queue = mutable.Queue[MqMessage]()
    val backpressure = new Semaphore(settings.bufferSize)

    val logic: GraphStageLogic = new GraphStageLogic(shape) {
      val onConnect = getAsyncCallback[IMqttAsyncClient] { client =>
        log.info(s"Connected to '${settings.broker}'.")
        client.subscribe(settings.topic, 0)
      }
      val onMessage = getAsyncCallback[MqMessage] { msg =>
        if (isAvailable(out)) {
          pushMessage(msg)
        } else {
          queue.enqueue(msg)
        }
      }
      val onConnectionLost = getAsyncCallback[Throwable] { ex =>
        failStage(ex)
      }

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          if (queue.nonEmpty) {
            pushMessage(queue.dequeue())
          }
        }
      })

      override def preStart(): Unit = {
        val client = new MqttAsyncClient(
          settings.broker.url,
          settings.clientId,
          settings.persistence
        )
        client.setCallback(new MqttCallback {
          def messageArrived(topic: String, message: MqttMessage): Unit = {
            backpressure.acquire()
            onMessage.invoke(MqMessage(topic, ByteString(message.getPayload)))
          }

          def deliveryComplete(token: IMqttDeliveryToken): Unit = ()

          def connectionLost(cause: Throwable): Unit =
            onConnectionLost.invoke(cause)
        })
        val connectOptions = new MqttConnectOptions
        connectOptions.setUserName(settings.user)
        connectOptions.setPassword(settings.pass.toCharArray)
        log.info(s"Connecting to '${settings.broker}'...")
        client.connect(connectOptions, (), new IMqttActionListener {
          override def onSuccess(asyncActionToken: IMqttToken): Unit =
            onConnect.invoke(client)

          override def onFailure(asyncActionToken: IMqttToken, exception: Throwable): Unit =
            onConnectionLost.invoke(exception)
        })
        super.preStart()
      }

      def pushMessage(message: MqMessage): Unit = {
        push(out, message)
        backpressure.release()
      }
    }
    (logic, subscriptionPromise.future)
  }

  override def shape = SourceShape(out)
}
