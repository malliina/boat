package com.malliina.boat.db

import cats.effect.IO
import cats.implicits.*
import com.malliina.boat.{BoatConf, BoatUser, DateVal, DeviceId, DeviceName, Language, MUnitSuite, RawSentence, SourceType, TrackId, TrackInput, TrackName}
import com.malliina.database.{Conf, DoobieDatabase}
import com.malliina.http.UrlSyntax.url
import com.malliina.values.Literals.{pass, user}
import com.malliina.values.Username
import fs2.Chunk
import fs2.io.file.Path
import org.typelevel.ci.CIStringSyntax

import java.time.LocalDate
import scala.annotation.unused
import scala.concurrent.duration.{Duration, DurationInt}

class TracksImporter extends MUnitSuite:
  def testConf: Conf = Conf(
    url"jdbc:mariadb://localhost:3307/boat",
    "boat",
    pass"changeme",
    BoatConf.mariaDbDriver,
    maxPoolSize = 2,
    autoMigrate = true,
    schemaTable = "flyway_schema_history2"
  )
  def dbResource = databaseFixture(testConf)
  val file = Path.fromNioPath(userHome.resolve(".boat/log.txt"))

  override def munitIOTimeout: Duration = 12.hours

  private def sequentially[T](tasks: List[IO[T]], acc: List[T]): IO[List[T]] =
    tasks match
      case head :: tail => head.flatMap(t => sequentially(tail, acc :+ t))
      case Nil          => IO.pure(acc)

  dbResource.test("import tracks from plotter log file".ignore): db =>
    val days = List(
      LocalDate.of(2026, 7, 11),
      LocalDate.of(2026, 7, 12),
      LocalDate.of(2026, 7, 14),
      LocalDate.of(2026, 7, 16),
      LocalDate.of(2026, 7, 17),
      LocalDate.of(2026, 7, 18)
    )
    sequentially(days.map(day => importByDay(file, day, db)), Nil)

  test("split by date".ignore):
    val what: IO[List[(LocalDate, Chunk[RawSentence])]] =
      TrackStreams[IO]().fileByDate(file).compile.toList
    what.map: list =>
      list
        .sortBy(_._1)
        .foreach: (date, chunk) =>
          println(s"$date: ${chunk.size}")

  dbResource.test("modify tracks".ignore): db =>
    val oldTrack = TrackId.unsafe(15044L)
    splitTracksByDate(oldTrack, TrackInserter(db))

  dbResource.test("update aggregates".ignore): db =>
    val track = TrackId.unsafe(15044L)
    db.run:
      TrackInserter(db).updateAggregates(track)

  @unused
  private def importByDay(file: Path, day: LocalDate, db: DoobieDatabase[IO]): IO[Long] =
    val inserts = TrackInserter(db)
    val importer = TrackImporter(inserts)
    val trackName = TrackName.random()
    val user = BoatUser(
      trackName,
      DeviceName.unsafe(ci"Amina"),
      SourceType.Boat,
      user"mle",
      Language.default
    )
    inserts
      .joinAsSource(user)
      .flatMap: track =>
        importer.save(importer.sentencesForDay(file, day), track.track.short, None)

  def splitTracksByDate(oldTrack: TrackId, db: TrackInserter[IO]) =
    def createAndUpdateTrack(date: DateVal) =
      val in = TrackInput.empty(TrackName.random(), DeviceId.unsafe(14))
      for
        newTrack <- db.insertTrack(in)
        updated <- db.changeTrack(oldTrack, date, newTrack.track)
      yield updated

    val action = for
      dates <- db.dates(oldTrack)
      updates <- dates.traverse(date => createAndUpdateTrack(date))
    yield updates
    db.db.run(action)

//  dbResource.test("insert user".ignore) { db =>
//    val users = DoobieUserManager(db)
//    val email = Email("santa@example.com")
//    val action =
//      for u <- users.register(email)
//      yield u
//    action.unsafeRunSync()
//  }

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
