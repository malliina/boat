package com.malliina.boat.db

import java.sql.{Timestamp, Types}
import java.time.Instant

import com.malliina.boat.{Coord, JoinedTrack, TrackPointRow}
import com.malliina.measure.DistanceM
import com.malliina.values.Username
import com.zaxxer.hikari.HikariDataSource
import io.getquill._

import scala.concurrent.ExecutionContext

class TestDatabase(val ds: HikariDataSource)(implicit val ec: ExecutionContext)
    extends NewMappings {
  val naming: SnakeCase = NamingStrategy(SnakeCase)
  lazy val ctx =
    new MysqlJdbcContext(naming, ds) with Quotes[MySQLDialect, SnakeCase]
  import ctx._

//  def test() = {
//    val c1 = Coord.buildOrFail(24, 60)
//    val optC = Option(c1)
//    optC.map { c =>
//      runIO(selectDistance(lift(c1), lift(c)))
//    }.getOrElse {
//      runIO(infix"SELECT 1".as[Int]).map(_ => DistanceM.zero)
//    }
//  }

  def hmm() = {
    val now = Instant.now()
    val nowOpt: Option[Instant] = Option(now)

    implicit class InstantQuotes(left: Instant) {
      def >=(right: Instant) = quote(infix"$left >= $right".as[Boolean])
      def <=(right: Instant) = quote(infix"$left <= $right".as[Boolean])
    }

    implicit val ie: Encoder[Instant] = encoder(
      Types.TIMESTAMP,
      (idx, value, row) => row.setTimestamp(idx, new Timestamp(value.toEpochMilli))
    )
    val rangedCoords = quote { (from: Option[Instant], to: Option[Instant]) =>
      rawPointsTable.filter { p =>
        from.forall(f => p.added >= f) && to.forall(t => p.added <= t)
      }
    }
    val tq = quote {
      nonEmptyTracks
        .filter(_.boat.username == lift(Username("mle")))
        .sortBy(_.trackAdded)(Ord.desc)
        .take(1)
    }
    val q = quote {
      for {
        t <- tq
        c <- rangedCoords(lift(nowOpt), lift(nowOpt))
        if t.track == c.track
      } yield TrackCoord(t, c)
    }
    val total = quote {
      q.sortBy(_.row.trackIndex)(Ord.desc)
        .drop(lift(2))
        .take(lift(3))
    }
    run(total)
  }
}
