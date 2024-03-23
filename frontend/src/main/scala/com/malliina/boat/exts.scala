package com.malliina.boat

import cats.effect.Sync
import cats.effect.kernel.Concurrent
import fs2.Stream

extension [F[_]: Sync, O](s: Stream[F, O])
  def tap[A](thunk: O => A): Stream[F, O] = s.evalTap: o =>
    Sync[F].delay(thunk(o))
extension [F[_]: Concurrent, O](s: Stream[F, O])
  def runInBackground =
    Stream.emit(()).concurrently(s).compile.resource.lastOrError
