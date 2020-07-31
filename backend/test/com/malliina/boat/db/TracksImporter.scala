package com.malliina.boat.db

import akka.stream.IOResult
import akka.stream.scaladsl.Source
import com.malliina.boat.{BoatName, BoatUser, DateVal, DeviceId, LocalConf, RawSentence, TrackId, TrackInput, TrackNames}
import com.malliina.util.FileUtils
import com.malliina.values.Username
import tests.AsyncSuite

import scala.concurrent.Future
import scala.concurrent.duration.DurationLong

class TracksImporter extends AsyncSuite {
  lazy val c = Conf.fromConf(LocalConf.localConf).toOption.get
  lazy val db = BoatDatabase(dbExecutor, c)

  test("import tracks from plotter log file".ignore) {
//    importSlice(".boat/Log20200513.txt", 1273831, 1320488)
    importSlice(".boat/latest.txt", 1233566, 1350930)
  }

  test("modify tracks".ignore) {
    val oldTrack = TrackId(175)
    splitTracksByDate(oldTrack, TrackInserts(db))
  }

  private def importSlice(file: String, drop: Int, last: Int) = {
    val inserts = TrackInserts(db)
    val importer = new TrackImporter(inserts)
    val trackName = TrackNames.random()
    val track = await(
      inserts.joinAsBoat(BoatUser(trackName, BoatName("Amina"), Username("mle"))),
      10.seconds
    )
    val s: Source[RawSentence, Future[IOResult]] =
      importer
        .fileSource(FileUtils.userHome.resolve(file))
        .drop(drop)
        .take(last - drop)
    val task = importer.saveSource(s, track.short)
    await(task, 300000.seconds)
  }

  def splitTracksByDate(oldTrack: TrackId, db: TrackInserts) = {
//    import db._
    import db.db._

    def createAndUpdateTrack(date: DateVal): IO[RunActionResult, Effect.Write] = {
      val in = TrackInput.empty(TrackNames.random(), DeviceId(14))
      for {
        newTrack <- runIO(tracksInsert(lift(in)))
        updated <- runIO(
          quote {
            rawPointsTable
              .filter(p => p.track == lift(oldTrack) && (dateOf(p.boatTime) == lift(date)))
              .update(_.track -> lift(newTrack))
          }
        )
      } yield updated
    }

    val action = for {
      dates <- runIO(
        pointsTable
          .filter(_.track == lift(oldTrack))
          .map(_.date)
          .distinct
      )
      updates <- IO.traverse(dates)(date => createAndUpdateTrack(date))
    } yield updates
    performIO(action)
  }
}
