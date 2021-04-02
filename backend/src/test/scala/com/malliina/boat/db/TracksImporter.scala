package com.malliina.boat.db

import cats.implicits._
import com.malliina.boat.{BoatConf, DateVal, DeviceId, TrackId, TrackInput, TrackNames}
import tests.MUnitSuite

class TracksImporter extends MUnitSuite {
  val dbResource = resourceFixture {
    blocker.flatMap { b =>
      DoobieDatabase(BoatConf.load.db, b)
    }
  }

  dbResource.test("import tracks from plotter log file".ignore) { db =>
////    importSlice(".boat/Log20200513.txt", 1273831, 1320488)
//    importSlice(".boat/latest.txt", 1233566, 1350930, db.resource)
  }

  dbResource.test("modify tracks".ignore) { db =>
    val oldTrack = TrackId(175)
    splitTracksByDate(oldTrack, DoobieTrackInserts(db.resource))
  }

//  private def importSlice(file: String, drop: Int, last: Int, db: DoobieDatabase) = {
//    val inserts = DoobieTrackInserts(db)
//    val importer = new TrackImporter(inserts)
//    val trackName = TrackNames.random()
//    val track = await(
//      inserts.joinAsBoat(BoatUser(trackName, BoatName("Amina"), Username("mle"))),
//      10.seconds
//    )
//    val s: Source[RawSentence, Future[IOResult]] =
//      importer
//        .fileSource(FileUtils.userHome.resolve(file))
//        .drop(drop)
//        .take(last - drop)
//    val task = importer.saveSource(s, track.short)
//    await(task, 300000.seconds)
//  }
//
  def splitTracksByDate(oldTrack: TrackId, db: DoobieTrackInserts) = {
    def createAndUpdateTrack(date: DateVal) = {
      val in = TrackInput.empty(TrackNames.random(), DeviceId(14))
      for {
        newTrack <- db.insertTrack(in)
        updated <- db.changeTrack(oldTrack, date, newTrack.track)
      } yield updated
    }

    val action = for {
      dates <- db.dates(oldTrack)
      updates <- dates.traverse(date => createAndUpdateTrack(date))
    } yield updates
    db.db.run(action)
  }
}
