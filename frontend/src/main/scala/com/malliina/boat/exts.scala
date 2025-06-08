package com.malliina.boat

import cats.effect.Sync
import fs2.Stream

extension [F[_]: Sync, O](s: Stream[F, O])
  def tap[A](thunk: O => A): Stream[F, O] = s.evalTap: o =>
    Sync[F].delay(thunk(o))
