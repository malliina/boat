package com.malliina.boat

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Keep, Sink, Source}

object Streams extends Streams

trait Streams {
  def rights[L, R](src: Source[Either[L, R], NotUsed]): Source[R, NotUsed] =
    src.flatMapConcat(e => e.fold(_ => Source.empty, c => Source.single(c)))

  def lefts[L, R](src: Source[Either[L, R], NotUsed]): Source[L, NotUsed] =
    src.flatMapConcat(e => e.fold(err => Source.single(err), _ => Source.empty))

  /** Processes input elements in `src` using `processorProps` such that processed elements will be available in the
    * returned Source. The processor must send processed elements of type U to the input actor in `processorProps`.
    *
    * @tparam T type of input
    * @tparam U type of output
    * @return
    */
  def couple[T, U](src: Source[T, NotUsed], processorProps: ActorRef => Props, as: ActorSystem)(implicit mat: Materializer): Source[U, NotUsed] = {
    val (sink, source) = connected[T, U](processorProps, as)
    src.runWith(sink)
    source
  }

  def connected[T, U](processorProps: ActorRef => Props, as: ActorSystem)(implicit mat: Materializer): (Sink[T, NotUsed], Source[U, NotUsed]) = {
    val publisherSink = Sink.asPublisher[U](fanout = true)
    val (processedActor, publisher) = Source.actorRef[U](65536, OverflowStrategy.fail).toMat(publisherSink)(Keep.both).run()
    val processed = Source.fromPublisher(publisher)
    val processor = as.actorOf(processorProps(processedActor))
    val processorSink = Sink.actorRef[T](processor, ())
    (processorSink, processed)
  }
}
