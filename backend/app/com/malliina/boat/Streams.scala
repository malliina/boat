package com.malliina.boat

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Props, Status}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}

object Streams extends Streams

trait Streams {

  /** The publisher-dance makes it so that even with multiple subscribers, `once` only runs once.
    * Without this wrapping, `once` executes independently for each subscriber, which is undesired
    * if `once` involves a side-effect (e.g. a database insert operation).
    *
    * @param once source to only run once for each emitted element
    * @tparam T type of element
    * @tparam U materialized value
    * @return a Source that supports multiple subscribers, but does not independently run `once` for each
    */
  def onlyOnce[T, U](once: Source[T, U])(implicit mat: Materializer): Source[T, NotUsed] =
    Source.fromPublisher(once.runWith(Sink.asPublisher(fanout = true)))

  def rights[L, R](src: Source[Either[L, R], NotUsed]): Source[R, NotUsed] =
    src.flatMapConcat(e => e.fold(_ => Source.empty, c => Source.single(c)))

  def lefts[L, R](src: Source[Either[L, R], NotUsed]): Source[L, NotUsed] =
    src.flatMapConcat(e => e.fold(err => Source.single(err), _ => Source.empty))

  /** Processes input elements in `src` using `processorProps` such that processed elements will be
    * available in the returned Source. The processor must send processed elements of type U to the
    * input actor in `processorProps`.
    *
    * @tparam T type of input
    * @tparam U type of output
    */
  def actorProcessed[T, U](src: Source[T, NotUsed],
                           processorProps: ActorRef => Props,
                           as: ActorSystem)(implicit mat: Materializer): Source[U, NotUsed] = {
    val flow: Flow[T, U, NotUsed] = connected[T, U](processorProps, as)
    src.via(flow)
  }

  def connected[T, U](processorProps: ActorRef => Props, as: ActorSystem)(
      implicit mat: Materializer): Flow[T, U, NotUsed] = {
    val publisherSink = Sink.asPublisher[U](fanout = true)
    val (processedActor, publisher) =
      Source.actorRef[U](65536, OverflowStrategy.fail).toMat(publisherSink)(Keep.both).run()
    val processed = Source.fromPublisher(publisher)
    val processor = as.actorOf(processorProps(processedActor))
    val processorSink = Sink.actorRef[T](processor, Status.Success("Done."))
    Flow.fromSinkAndSource(processorSink, processed)
  }
}
