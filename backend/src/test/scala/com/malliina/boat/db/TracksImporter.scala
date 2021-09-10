package com.malliina.boat.db

import cats.effect.IO
import cats.implicits.*
import com.malliina.boat.{BoatConf, BoatName, BoatUser, DateVal, DeviceId, LocalConf, TrackId, TrackInput, TrackNames}
import com.malliina.values.Email
import tests.{MUnitSuite, WrappedTestConf}

class TracksImporter extends MUnitSuite:
  def testConf = WrappedTestConf.parse().map(_.boat.testdb).fold(e => throw e, identity)
  val dbResource = databaseFixture(testConf)
  val file = userHome.resolve(".boat/LogNYY.txt")

  dbResource.test("import tracks from plotter log file".ignore) { db =>
////    importSlice(".boat/Log20200513.txt", 1273831, 1320488)
//    importSlice(".boat/latest.txt", 1233566, 1350930, db.resource)
  }

  dbResource.test("modify tracks".ignore) { db =>
    val oldTrack = TrackId(175)
    splitTracksByDate(oldTrack, TrackInserter(db))
  }

  dbResource.test("read file".ignore) { database =>
    val users = DoobieUserManager(database)
    val rows = blocker.use[IO, Long] { b =>
      val inserter = TrackInserter(database)
      val i = TrackImporter(inserter, munitContextShift, b)
      val user = users.userInfo(Email("mleski123@gmail.com")).unsafeRunSync()
      val track = inserter
        .joinAsBoat(BoatUser(TrackNames.random(), BoatName("Amina"), user.username))
        .unsafeRunSync()
      i.saveFile(file, track.short)
    }
//    println("test")
    println(rows.unsafeRunSync())
  }

  test("split by date".ignore) {
//    TrackImporter.byDate()
  }

//  private def importSlice(file: String, drop: Int, last: Int, db: DoobieDatabase) = {
//    val inserts = TrackInserter(db)
//    val importer = new TrackImporter(inserts)
//    val trackName = TrackNames.random()
//    val track =
//      inserts.joinAsBoat(BoatUser(trackName, BoatName("Amina"), Username("mle"))).unsafeRunSync()
//    val s: Source[RawSentence, Future[IOResult]] =
//      importer
//        .fileSource(FileUtils.userHome.resolve(file))
//        .drop(drop)
//        .take(last - drop)
//    val task = importer.saveSource(s, track.short)
//    await(task, 300000.seconds)
//  }
//
  def splitTracksByDate(oldTrack: TrackId, db: TrackInserter) =
    def createAndUpdateTrack(date: DateVal) =
      val in = TrackInput.empty(TrackNames.random(), DeviceId(14))
      for
        newTrack <- db.insertTrack(in)
        updated <- db.changeTrack(oldTrack, date, newTrack.track)
      yield updated

    val action = for
      dates <- db.dates(oldTrack)
      updates <- dates.traverse(date => createAndUpdateTrack(date))
    yield updates
    db.db.run(action)
