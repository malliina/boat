package com.malliina.tasks

import cats.effect.Concurrent
import cats.effect.kernel.Resource
import fs2.Stream

extension [F[_]: Concurrent, O](s: Stream[F, O])
  def runInBackground: Resource[F, Unit] =
    Stream.emit(()).concurrently(s).compile.resource.lastOrError
