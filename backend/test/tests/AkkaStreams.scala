package tests

import java.time.{LocalDate, LocalTime}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, RunnableGraph, Sink, Source}
import akka.stream.{KillSwitches, UniqueKillSwitch}
import com.malliina.boat.parsing._
import com.malliina.boat.{Coord, KeyedSentence, RawSentence, SentenceKey}
import com.malliina.measure.{DistanceIntM, SpeedIntM, TemperatureInt}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.WebSocket

class AkkaStreams extends BaseSuite {
  implicit val as = ActorSystem("test")

  test("stateful sentence parsing") {
    val from = MultiParsingTests.testFrom

    def keyed(id: Long) = KeyedSentence(SentenceKey(id), RawSentence(""), from)

    val testTemp = WaterTemperature(10.celsius, keyed(1))
    val testSpeed = ParsedBoatSpeed(40.knots, keyed(2))
    val testDepth = WaterDepth(10.meters, 0.meters, keyed(3))
    val parsed = Source[ParsedSentence](
      List(
        testTemp,
        testSpeed,
        testDepth,
        ParsedDateTime(LocalDate.of(2018, 4, 10), LocalTime.of(10, 11, 1), keyed(4)),
        ParsedCoord(Coord.buildOrFail(1, 2), LocalTime.of(10, 11, 1), keyed(5)),
        ParsedDateTime(LocalDate.of(2018, 4, 10), LocalTime.of(10, 12, 2), keyed(6)),
        ParsedCoord(Coord.buildOrFail(4, 5), LocalTime.of(10, 12, 2), keyed(7)),
        ParsedDateTime(LocalDate.of(2018, 4, 11), LocalTime.of(10, 13, 3), keyed(8)),
        ParsedCoord(Coord.buildOrFail(6, 7), LocalTime.of(10, 13, 3), keyed(9)),
        ParsedCoord(Coord.buildOrFail(8, 9), LocalTime.of(0, 1, 4), keyed(10))
      )
    )

    def toFull(coord: Coord, time: LocalTime, date: LocalDate, keys: Seq[Long]) =
      FullCoord(
        coord,
        time,
        date,
        testSpeed.speed,
        testTemp.temp,
        testDepth.depth,
        testDepth.offset,
        from,
        keys.map(SentenceKey.apply)
      )

    val expected = List(
      toFull(
        Coord.buildOrFail(1, 2),
        LocalTime.of(10, 11, 1),
        LocalDate.of(2018, 4, 10),
        Seq(5, 4, 2, 1, 3)
      ),
      toFull(
        Coord.buildOrFail(4, 5),
        LocalTime.of(10, 12, 2),
        LocalDate.of(2018, 4, 10),
        Seq(7, 6, 2, 1, 3)
      ),
      toFull(
        Coord.buildOrFail(6, 7),
        LocalTime.of(10, 13, 3),
        LocalDate.of(2018, 4, 11),
        Seq(9, 8, 2, 1, 3)
      ),
      toFull(
        Coord.buildOrFail(8, 9),
        LocalTime.of(10, 13, 3),
        LocalDate.of(2018, 4, 11),
        Seq(10, 8, 2, 1, 3)
      )
    )
    val processed = BoatParser.multi(parsed).take(4)
    val listSink =
      Sink.fold[List[FullCoord], FullCoord](List.empty[FullCoord])((acc, dc) => acc :+ dc)
    val actual = await(processed.runWith(listSink))
    assert(actual == expected)
  }

  test("one-time side-effect".ignore) {
    val orig = Source.tick(1.second, 1.second, "msg").take(1)
    // only side-effects once, if others run `src` instead of `effected`
    val effected = orig.map { msg =>
      println(msg); s"$msg!"
    }
    val pub = effected.runWith(Sink.asPublisher(fanout = true))
    val src = Source.fromPublisher(pub)
    val f1 = src.runForeach(println)
    val f2 = src.runForeach(println)
    await(f1)
    await(f2)
  }

  test("MergeHub".ignore) {
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

  test("BroadcastHub".ignore) {
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
    fromProducer.runForeach(msg => println("consumer1: " + msg))
    fromProducer.runForeach(msg => println("consumer2: " + msg))
    Thread.sleep(3000)
  }

  test("dynamic publishers to common Sink".ignore) {
    val consumer = Sink.foreach(println)
    val runnableGraph: RunnableGraph[Sink[String, NotUsed]] =
      MergeHub.source[String](perProducerBufferSize = 16).to(consumer)
    val incoming = runnableGraph.run()
    val outgoing = Source.maybe[JsValue]
    Flow.fromSinkAndSource(incoming, outgoing)
  }

  test("dynamic listeners to common Source".ignore) {
    val commonSource = Source.tick(1.second, 1.second, Json.obj("msg" -> "New message"))
    val runnableGraph: RunnableGraph[Source[JsValue, NotUsed]] =
      commonSource.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right)
    val incoming = Sink.foreach[JsValue](println)
    val outgoing = runnableGraph.run()
    val _ = Flow.fromSinkAndSource(incoming, outgoing)
  }

  test("pubsub".ignore) {
    val (sink, source) = MergeHub
      .source[JsValue](perProducerBufferSize = 16)
      .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
      .run()
    val _ = source.runWith(Sink.foreach(println))
    val busFlow: Flow[JsValue, JsValue, UniqueKillSwitch] =
      Flow
        .fromSinkAndSource(sink, source)
        .joinMat(KillSwitches.singleBidi[JsValue, JsValue])(Keep.right)
        .backpressureTimeout(3.seconds)

    sink.runWith(Source.single(Json.obj("msg" -> "hi")))
    sink.runWith(Source.single(Json.obj("msg" -> "hi2")))
  }

  test("socket stream".ignore) {
    WebSocket.accept[JsValue, JsValue] { rh =>
      Flow[JsValue].map { json =>
        Json.obj("echo" -> json)
      }
    }
  }

}
