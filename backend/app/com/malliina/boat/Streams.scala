package com.malliina.boat

import akka.NotUsed
import akka.stream.scaladsl.Source

object Streams extends Streams

trait Streams {
  def rights[L, R](src: Source[Either[L, R], NotUsed]): Source[R, NotUsed] =
    src.flatMapConcat(e => e.fold(_ => Source.empty, c => Source.single(c)))

  def lefts[L, R](src: Source[Either[L, R], NotUsed]): Source[L, NotUsed] =
    src.flatMapConcat(e => e.fold(err => Source.single(err), _ => Source.empty))
}
