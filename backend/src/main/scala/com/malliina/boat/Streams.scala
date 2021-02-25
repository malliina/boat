package com.malliina.boat

import akka.actor.{ActorRef, ActorSystem, Props, Status}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{CompletionStrategy, Materializer, OverflowStrategy}
import akka.{Done, NotUsed}
import com.malliina.boat.Streams.log
import com.malliina.util.AppLogger
import org.reactivestreams.Publisher

import scala.concurrent.{ExecutionContext, Future}

object Streams extends Streams {
  private val log = AppLogger(getClass)
}

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
  def actorProcessed[T, U](
    src: Source[T, NotUsed],
    processorProps: ActorRef => Props,
    as: ActorSystem
  )(implicit mat: Materializer): Source[U, NotUsed] = {
    val flow: Flow[T, U, NotUsed] = connected[T, U](processorProps, as)
    src.via(flow)
  }

  def connected[T, U](processorProps: ActorRef => Props, as: ActorSystem)(implicit
    mat: Materializer
  ): Flow[T, U, NotUsed] = {
    val publisherSink: Sink[U, Publisher[U]] = Sink.asPublisher[U](fanout = true)
    val completion: PartialFunction[Any, CompletionStrategy] = {
      case Status.Success(s: CompletionStrategy) => s
      case Status.Success(_)                     => CompletionStrategy.draining
      case Status.Success                        => CompletionStrategy.draining
    }
    val failure: PartialFunction[Any, Throwable] = {
      case Status.Failure(cause) => cause
    }
    val (processedActor: ActorRef, publisher) =
      Source
        .actorRef[U](completion, failure, 65536, OverflowStrategy.fail)
        .toMat(publisherSink)(Keep.both)
        .run()
    val processed = Source.fromPublisher(publisher)
    val processor = as.actorOf(processorProps(processedActor))
    val processorSink = Sink.actorRef[T](processor, Status.Success("Done."), t => Status.Failure(t))
    Flow.fromSinkAndSource(processorSink, processed)
  }

  def monitored[In, Mat](src: Source[In, Mat], label: String)(implicit
    ec: ExecutionContext
  ): Source[In, Future[Done]] =
    src.watchTermination()(Keep.right).mapMaterializedValue { done =>
      done.transform { tryDone =>
        tryDone.fold(
          t => log.error(s"Error in flow '$label'.", t),
          _ => log.warn(s"Flow '$label' completed.")
        )
        tryDone
      }
    }
}
