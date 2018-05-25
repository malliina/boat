package tests

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitches, UniqueKillSwitch}
import org.scalatest.FunSuite
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.WebSocket

class AkkaStreams extends FunSuite {
  implicit val mat = ActorMaterializer()(ActorSystem("test"))

  ignore("one-time side-effect") {
    val orig = Source.tick(1.second, 1.second, "msg").take(1)
    // only side-effects once, if others run `src` instead of `effected`
    val effected = orig.map { msg => println(msg); s"$msg!" }
    val pub = effected.runWith(Sink.asPublisher(fanout = true))
    val src = Source.fromPublisher(pub)
    val f1 = src.runForeach(println)
    val f2 = src.runForeach(println)
    await(f1)
    await(f2)
  }

  ignore("MergeHub") {
    val consumer = Sink.foreach(println)

    // Attach a MergeHub Source to the consumer. This will materialize to a
    // corresponding Sink.
    val runnableGraph: RunnableGraph[Sink[String, NotUsed]] =
    MergeHub.source[String](perProducerBufferSize = 16).to(consumer)

    // By running/materializing the consumer we get back a Sink, and hence
    // now have access to feed elements into it. This Sink can be materialized
    // any number of times, and every element that enters the Sink will
    // be consumed by our consumer.
    val toConsumer: Sink[String, NotUsed] = runnableGraph.run()

    // Feeding two independent sources into the hub.
    Source.single("Hello!").runWith(toConsumer)
    Source.single("Hub!").runWith(toConsumer)
  }

  ignore("BroadcastHub") {
    // A simple producer that publishes a new "message" every second
    val producer = Source.tick(1.second, 1.second, "New message")

    // Attach a BroadcastHub Sink to the producer. This will materialize to a
    // corresponding Source.
    // (We need to use toMat and Keep.right since by default the materialized
    // value to the left is used)
    val runnableGraph: RunnableGraph[Source[String, NotUsed]] =
    producer.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right)

    // By running/materializing the producer, we get back a Source, which
    // gives us access to the elements published by the producer.
    val fromProducer: Source[String, NotUsed] = runnableGraph.run()

    // Print out messages from the producer in two independent consumers
    fromProducer.runForeach(msg ⇒ println("consumer1: " + msg))
    fromProducer.runForeach(msg ⇒ println("consumer2: " + msg))
    Thread.sleep(3000)
  }

  ignore("dynamic publishers to common Sink") {
    val consumer = Sink.foreach(println)
    val runnableGraph: RunnableGraph[Sink[String, NotUsed]] =
      MergeHub.source[String](perProducerBufferSize = 16).to(consumer)
    val incoming = runnableGraph.run()
    val outgoing = Source.maybe[JsValue]
    Flow.fromSinkAndSource(incoming, outgoing)
  }

  ignore("dynamic listeners to common Source") {
    val commonSource = Source.tick(1.second, 1.second, Json.obj("msg" -> "New message"))
    val runnableGraph: RunnableGraph[Source[JsValue, NotUsed]] =
      commonSource.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right)
    val incoming = Sink.foreach[JsValue](println)
    val outgoing = runnableGraph.run()
    val _ = Flow.fromSinkAndSource(incoming, outgoing)
  }

  ignore("pubsub") {
    val (sink, source) = MergeHub.source[JsValue](perProducerBufferSize = 16)
      .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
      .run()
    val _ = source.runWith(Sink.foreach(println))
    val busFlow: Flow[JsValue, JsValue, UniqueKillSwitch] =
      Flow.fromSinkAndSource(sink, source)
        .joinMat(KillSwitches.singleBidi[JsValue, JsValue])(Keep.right)
        .backpressureTimeout(3.seconds)

    sink.runWith(Source.single(Json.obj("msg" -> "hi")))
    sink.runWith(Source.single(Json.obj("msg" -> "hi2")))
  }

  ignore("socket stream") {
    WebSocket.accept[JsValue, JsValue] { rh =>
      Flow[JsValue].map { json =>
        Json.obj("echo" -> json)
      }
    }
  }


}
