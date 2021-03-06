package com.malliina.boat.db

import cats.implicits._
import com.malliina.boat.{FairwayInfo, FeatureCollection}
import doobie.implicits._
import play.api.libs.json.Json
import tests.{MUnitDatabaseSuite, MUnitSuite}

import java.nio.file.Files

class FairwayDatabaseTests extends MUnitSuite with MUnitDatabaseSuite {
  doobieDb.test("import fairways to database".ignore) { resource =>
    val database = resource.resource
    val fileIn = userHome.resolve(".boat/vaylat/vaylat-geo.json")
    val strIn = Files.readAllBytes(fileIn)
    val coll = Json.parse(strIn).as[FeatureCollection]
    val fs = coll.features
    val svc = DoobieFairwayService(database)

    val insertTask = fs.toList.traverse { f =>
      def coords(id: FairwayId) = f.geometry.coords.map { coord =>
        FairwayCoordInput(coord, coord.lat, coord.lng, coord.hash, id)
      }.toList
      val in = f.props.as[FairwayInfo]
      for {
        id <- svc.insert(in)
        cids <- svc.insertCoords(coords(id))
      } yield cids
    }
    val inserts = for {
      _ <- svc.delete
      cids <- insertTask
    } yield cids
    database.run(inserts).unsafeRunSync()
  }
}
