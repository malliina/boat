package com.malliina.boat.db

import java.nio.file.Files

import com.malliina.boat.{FairwayInfo, FeatureCollection}
import play.api.libs.json.Json
import tests.LegacyDatabase

import scala.concurrent.duration.DurationInt

class FairwayDatabaseTests extends LegacyDatabase {
  ignore("import fairways to database") {
    boatSchema.init()
    val db = testDatabase(ec)
    val fileIn = userHome.resolve(".boat/vaylat/vaylat-geo.json")
    val strIn = Files.readAllBytes(fileIn)
    val coll = Json.parse(strIn).as[FeatureCollection]
    val fs = coll.features
    val svc = NewFairwayService(db)
    import svc._
    import svc.db._
    val insertTask = IO.traverse(fs) { f =>
      def coords(id: FairwayId) = f.geometry.coords.map { coord =>
        FairwayCoordInput(coord, coord.lat, coord.lng, coord.hash, id)
      }
      val in = f.props.as[FairwayInfo]
      for {
        id <- runIO(
          fairwaysTable
            .insert(
              _.nameFi -> lift(in.nameFi),
              _.nameSe -> lift(in.nameSe),
              _.start -> lift(in.start),
              _.end -> lift(in.end),
              _.depth -> lift(in.depth),
              _.depth2 -> lift(in.depth2),
              _.depth3 -> lift(in.depth3),
              _.lighting -> lift(in.lighting),
              _.classText -> lift(in.classText),
              _.seaArea -> lift(in.seaArea),
              _.state -> lift(in.state)
            )
            .returningGenerated(_.id)
        )
        cids <- runIO(
          liftQuery(coords(id)).foreach { c =>
            fairwaysCoordsTable
              .insert(
                _.coord -> c.coord,
                _.latitude -> c.latitude,
                _.longitude -> c.longitude,
                _.coordHash -> c.coordHash,
                _.fairway -> c.fairway
              )
              .returningGenerated(_.id)
          }
        )
      } yield cids
    }
    val inserts = for {
      deletion <- runIO(fairwaysTable.delete)
      insertion <- insertTask
    } yield insertion
    await(performAsync("Import fairways")(inserts), 600.seconds)
  }
}
