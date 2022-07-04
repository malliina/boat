package com.malliina.boat.db

import cats.effect.IO
import cats.implicits.*
import com.malliina.boat.{BoatConf, BoatName, BoatUser, DateVal, DeviceId, LocalConf, RawSentence, TrackId, TrackInput, TrackMeta, TrackNames}
import com.malliina.values.{Email, Username}
import tests.{MUnitSuite, WrappedTestConf}
import fs2.io.file.Path
import fs2.{Chunk, Stream}
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import java.time.LocalDate

class TracksImporter extends MUnitSuite:
  def testConf = WrappedTestConf.parse().map(_.boat.testdb).fold(e => throw e, identity)
  def dbResource = databaseFixture(testConf)
  val file = Path.fromNioPath(userHome.resolve(".boat/log.txt"))

  override def munitTimeout: Duration = 6.hours

  dbResource.test("import tracks from plotter log file".ignore) { db =>
    val day = LocalDate.of(2022, 6, 2)
    importByDay(file, day, db)
  }

  test("split by date".ignore) {
    val what: IO[List[(LocalDate, Chunk[RawSentence])]] =
      TrackStreams().fileByDate(file).compile.toList
    what.map { list =>
      list.sortBy(_._1).foreach { case (date, chunk) =>
        println(s"$date: ${chunk.size}")
      }
    }
  }

  private def importByDay(file: Path, day: LocalDate, db: DoobieDatabase): IO[Long] =
    val inserts = TrackInserter(db)
    val importer = TrackImporter(inserts)
    val trackName = TrackNames.random()
    val user = BoatUser(trackName, BoatName("Amina"), Username("mle"))
    inserts.joinAsBoat(user).flatMap { track =>
      importer.save(importer.sentencesForDay(file, day), track.short)
    }

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

//  private def importSlice(file: String, drop: Int, last: Int, db: DoobieDatabase): IO[Long] =
//    val inserts = TrackInserter(db)
//    val importer = TrackImporter(inserts)
//    val trackName = TrackNames.random()
//    val track: TrackMeta =
//      inserts.joinAsBoat(BoatUser(trackName, BoatName("Amina"), Username("mle"))).unsafeRunSync()
//    val s: Source[RawSentence, Future[IOResult]] =
//      importer
//        .fileSource(FileUtils.userHome.resolve(file))
//        .drop(drop)
//        .take(last - drop)
//    importer.save(s, track.short)

//  dbResource.test("modify tracks".ignore) { db =>
//    val oldTrack = TrackId(175)
//    splitTracksByDate(oldTrack, TrackInserter(db))
//  }
//
//  dbResource.test("read file".ignore) { database =>
//    val users = DoobieUserManager(database)
//    val inserter = TrackInserter(database)
//    val i = TrackImporter(inserter)
//    val user = users.userInfo(Email("mleski123@gmail.com")).unsafeRunSync()
//    val track = inserter
//      .joinAsBoat(BoatUser(TrackNames.random(), BoatName("Amina"), user.username))
//      .unsafeRunSync()
//    val rows = i.saveFile(file, track.short)
////    println("test")
//    println(rows.unsafeRunSync())
//  }
