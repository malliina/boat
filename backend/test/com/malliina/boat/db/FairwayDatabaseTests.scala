package com.malliina.boat.db

import java.nio.file.Files

import com.malliina.boat.{CoordHash, FairwayInfo, FeatureCollection}
import com.malliina.concurrent.Execution.cached
import play.api.libs.json.Json

import concurrent.duration.DurationInt

class FairwayDatabaseTests extends DatabaseSuite {
//  ignore("describe route") {
//    val service = FairwayService(initDb())
//    val fs = await(service.fairways(CoordHash("19.62690,60.37960")))
//    assert(fs.exists(_.id === FairwayId(701)))
//  }

  ignore("import fairways to database") {
    val db = initDb()
    val fileIn = userHome.resolve(".boat/vaylat/vaylat-geo.json")
    val strIn = Files.readAllBytes(fileIn)
    val coll = Json.parse(strIn).as[FeatureCollection]
    val fs = coll.features
    import db.api._
    import db._
    await(db.run(db.fairwaysTable.delete))
    val inserts = DBIO
      .sequence(
        fs.map { f =>
          for {
            id <- db.fairwaysTable
              .map(_.forInserts)
              .returning(db.fairwaysTable.map(_.id)) += f.props.as[FairwayInfo]
            cs = f.geometry.coords.map { coord =>
              FairwayCoordInput(coord, coord.lat, coord.lng, coord.hash, id)
            }
            cids <- db.fairwayCoordsTable
              .map(_.forInserts)
              .returning(db.fairwayCoordsTable.map(_.id)) ++= cs
          } yield cids
        }
      )
      .transactionally
    await(db.run(inserts), 600.seconds)
  }
}
