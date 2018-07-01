package com.malliina.boat.db

import java.time.LocalDate

import com.malliina.boat.{AppConf, BoatId, TrackId, TrackInput, TrackNames}
import com.malliina.concurrent.ExecutionContexts.cached
import play.api.Mode
import tests.BaseSuite

import scala.concurrent.duration.DurationInt

class TracksDatabaseTests extends BaseSuite {
  ignore("modify tracks") {
    val db = BoatSchema(DatabaseConf(Mode.Prod, AppConf.localConf))
    import db._
    import db.api._
    import db.mappings._

    val oldTrack = TrackId(66)

    def createAndUpdateTrack(date: LocalDate) =
      for {
        newTrack <- trackInserts += TrackInput(TrackNames.random(), BoatId(14))
        updated <- updateTrack(oldTrack, date, newTrack)
      } yield updated

    def updateTrack(track: TrackId, date: LocalDate, newTrack: TrackId) =
      coordsTable.map(_.combined)
        .filter((t: LiftedCoord) => t.track === track && t.date === date)
        .map(_.track)
        .update(newTrack)

    val action = for {
      dates <- coordsTable.map(_.combined.date).distinct.sorted.result
      updates <- DBIO.sequence(dates.map(date => createAndUpdateTrack(date)))
    } yield updates
    await(db.run(action), 100.seconds)
  }
}
